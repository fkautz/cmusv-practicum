/*
 * $Id$
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.util;

import java.io.*;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.lockss.app.LockssApp;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.alias.CannotResolveClassException;
import com.thoughtworks.xstream.alias.ClassMapper;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.DataHolder;
import com.thoughtworks.xstream.core.*;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * <p>An implementation of {@link ObjectSerializer} based on
 * {@link <a href="http://xstream.codehaus.org/">XStream</a>}.</p>
 * <p>This implementation of {@link ObjectSerializer} is intended to
 * become the preferred way of serializing objects to XML in the
 * LOCKSS codebase because it is much simpler and much lighter than
 * its legacy counterpart, which was based on Castor.</p>
 * <p>It is generally <em>safe</em> to use the same XStreamSerializer
 * instance for multiple unrelated marshalling and unmarshalling
 * operations.</p>
 * <p>This class supports post-deserialization processing. To take
 * advantage of it, you can either use the traditional Java post-
 * deserialization convention with {@link Serializable}, or you can
 * implement {@link org.lockss.util.LockssSerializable}. In that case,
 * your class has to define a method named <code>postUnmarshal</code>
 * that accepts one parameter of type {@link LockssApp} and returns
 * <code>void</code>. Although the underlying implementation does not
 * enforce it, it is <em>strongly recommended</em> that the post-
 * deserialization method be <code>protected</code> so that it can
 * call on post-deserialization methods in a superclass.</p>
 * @author Thib Guicherd-Callin
 */
public class XStreamSerializer extends ObjectSerializer {

  /*
   * IMPLEMENTATION NOTES
   * 
   * This class is essentially just a wrapper around the XStream
   * class. Because XStream can marshal/unmarshal nearly arbitrary
   * objects (at least as far as the complexity of the LOCKSS codebase
   * goes), its interface was used as a model for ObjectSerializer,
   * hence the vague uselessness of this class. (ObjectSerializer is
   * necessary to refactor Castor-specific code into Castor-specific
   * implementations of serialization. Before ObjectSerializer existed
   * there was Castor-aware code in several data structures that just
   * had no business knowing about XML mappings.)
   */
  
  /*
   * begin PRIVATE STATIC INNER CLASS
   * ================================
   */
  /**
   * <p>A runtime exception used internally to
   * {@link XStreamSerializer} only.</p>
   * @author Thib Guicherd-Callin
   */
  private static class LockssNotSerializableException
      extends RuntimeException {
    
    /*
     * IMPLEMENTATION NOTES
     * 
     * With this class, it's possible to throw an exception that is
     * specifically recognizable as being internal to the custom
     * marshalling/unmarshallin strategies defined in this file,
     * without changing the signature of the methods involved in the
     * XStream API.
     */
    
  }
  /*
   * end PRIVATE STATIC INNER CLASS
   * ==============================
   */
  
  /*
   * begin PRIVATE STATIC INNER CLASS
   * ================================
   */
  /**
   * <p>A custom implementation of {@link ReferenceByXPathMarshaller}
   * that checks that serialized object graphs are
   * {@link Serializable}-or-{@link LockssSerializable}.</p>
   * @author Thib Guicherd-Callin
   */
  private static class LockssReferenceByXPathMarshaller
      extends ReferenceByXPathMarshaller {

    /**
     * <p>Builds a new marshaller.</p>
     * @param writer
     * @param converterLookup
     * @param classMapper
     */
    public LockssReferenceByXPathMarshaller(HierarchicalStreamWriter writer,
                                            DefaultConverterLookup converterLookup,
                                            ClassMapper classMapper) {
      super(writer, converterLookup, classMapper);
    }

    /**
     * <p>A specialized version of
     * {@link ReferenceByXPathMarshaller#convertAnother} that throws
     * a {@link LockssNotSerializableException} if the argument is
     * not {@link Serializable} or {@link LockssSerializable} (and
     * that just invokes the super-implementation to do its work).</p>
     * @param parent {@inheritDoc}
     * @throws LockssNotSerializableException if obj is not
     *                                        {@link Serializable} or
     *                                        {@link LockssSerializable}.
     * @see {@link ReferenceByXPathMarshaller#convertAnother}
     */
    public void convertAnother(Object parent) {
      if ( !( parent instanceof Serializable ||
              parent instanceof LockssSerializable) ) {
        logger.debug2("Could not serialize an object of type "
            + parent.getClass().getName());
        throw new LockssNotSerializableException();
      }
      super.convertAnother(parent);
    }
    
  }
  /*
   * end PRIVATE STATIC INNER CLASS
   * ==============================
   */
  
  /*
   * begin PRIVATE STATIC INNER CLASS
   * ================================
   */
  /**
   * <p>This class is used to customize the way XStream traverses an
   * object graph during serialization and deserialization.</p>
   * <p>At serialization time, it checks that object graphs are
   * {@link Serializable}-or-{@link LockssSerializable}. At
   * deserialization time, it invokes any custom post-deserialization
   * methods in the object graph.</p>
   * @author Thib Guicherd-Callin
   * @see ReferenceByXPathMarshallingStrategy
   * @see LockssReferenceByXPathMarshaller
   * @see LockssReferenceByXPathUnmarshaller
   */
  private static class LockssReferenceByXPathMarshallingStrategy
      extends ReferenceByXPathMarshallingStrategy {

    /**
     * <p>Reference to the context object.</p>
     */
    private LockssApp lockssContext;
    
    /**
     * <p>Builds a new instance.</p>
     * @param lockssContext The context object (needed by the
     *                      unmarshaller).
     */
    public LockssReferenceByXPathMarshallingStrategy(LockssApp lockssContext) {
      this.lockssContext = lockssContext;
    }
    
    /**
     * <p>Performs unmarshalling with a
     * {@link LockssReferenceByXPathMarshaller} instance.</p>
     * @see LockssReferenceByXPathMarshaller
     */
    public void marshal(HierarchicalStreamWriter writer,
                        Object root,
                        DefaultConverterLookup converterLookup,
                        ClassMapper classMapper,
                        DataHolder dataHolder) {
      new LockssReferenceByXPathMarshaller(
          writer, converterLookup, classMapper).start(
              root, dataHolder);
    }

    /**
     * <p>Performs unmarshalling with a
     * {@link LockssReferenceByXPathUnmarshaller} instance.</p>
     * @see LockssReferenceByXPathUnmarshaller
     */
    public Object unmarshal(Object root,
                            HierarchicalStreamReader reader,
                            DataHolder dataHolder,
                            DefaultConverterLookup converterLookup,
                            ClassMapper classMapper) {
      return new LockssReferenceByXPathUnmarshaller(
          lockssContext, root, reader, converterLookup, classMapper).start(
              dataHolder);
    }

  }
  /*
   * end PRIVATE STATIC INNER CLASS
   * ==============================
   */

  /*
   * begin PRIVATE STATIC INNER CLASS
   * ================================
   */
  /**
   * <p>A custom implementation of {@link ReferenceByXPathUnmarshaller}
   * that performs post-deserialization processing on deserialized
   * object graphs.</p>
   * @author Thib Guicherd-Callin
   */
  private static class LockssReferenceByXPathUnmarshaller
      extends ReferenceByXPathUnmarshaller {
  
    /**
     * <p>A reference to the context object.</p>
     */
    private LockssApp lockssContext;
    
    /**
     * <p>Builds a new instance by invoking the superclass constructor
     * with the same argument list except for the first parameter
     * (which is just saved).</p>
     * @param lockssContext   A deserialization context object.
     * @param root
     * @param reader
     * @param converterLookup
     * @param classMapper
     */
    public LockssReferenceByXPathUnmarshaller(LockssApp lockssContext,
                                              Object root,
                                              HierarchicalStreamReader reader,
                                              ConverterLookup converterLookup,
                                              ClassMapper classMapper) {
      super(root, reader, converterLookup, classMapper);
      this.lockssContext = lockssContext;
    }
    
    /**
     * <p>Converts an object using the superclass mechanism, then
     * invokes the protected post-deserialization method
     * <code>postUnmarshal(org.lockss.app.LockssApp)</code> if it is
     * present.</p>
     * @param parent
     * @param type
     */
    public Object convertAnother(Object parent, Class type) {
      Object ret = super.convertAnother(parent, type);
      invokePostDeserializationMethod(ret);
      return ret;
    }
    
    /**
     * <p>Looks up the hidden post-deserialization method in an
     * object and caches it if found.</p>
     * @param obj The object being considered.
     * @return A {@link Method} object reflecting the hidden
     *         post-deserialization method in the object's class (or
     *         one of its superclasses), or null if there is no such
     *         method in the object's inheritance hierarchy.
     */
    private Method cachePostDeserializationMethod(Object obj) {
      Class objClass = obj.getClass();
      Method objMethod = null;
      
      // Look up inheritance hierarchy
      while (objClass != Object.class) {
        try {
          objMethod =
            objClass.getDeclaredMethod(POST_DESERIALIZATION_METHOD,
                                       POST_DESERIALIZATION_PARAMETERS);
          objClass = Object.class; // executed only if call succeeds
        }
        catch (NoSuchMethodException nsmE) {
          objClass = objClass.getSuperclass();
        }
      }
      
      // Cache result
      if (objMethod == null) {
        methodCache.put(obj.getClass(), NONE);
      }
      else {
        objMethod.setAccessible(true); // monstrous, monstrous
        methodCache.put(obj.getClass(), objMethod);
      }
      return objMethod;
    }
    
    /**
     * <p>An exception message formatter used when an exception is
     * thrown by the post-deserialization mechanism.</p>
     * @param exc The exception thrown by the underlying code.
     * @return A new ConversionException with <code>e</code> nested.
     */
    private ConversionException failDeserialize(Exception exc) {
      StringBuffer buffer = new StringBuffer();
      buffer.append("An exception of type ");
      buffer.append(exc.getClass().getName());
      buffer.append(" was thrown by an object while it was being deserialized.");
      return new ConversionException(buffer.toString(), exc);
    }

    /**
     * <p>Performs post-deserialization by invoking the hidden method
     * (if it exists).</p>
     * @param obj The freshly deserialized object.
     */
    private void invokePostDeserializationMethod(Object obj) {
      if (!(obj instanceof LockssSerializable)) {
        return; // only process LockssSerializable objects
      }
      
      Method met = lookupPostDeserializationMethod(obj);
      if (met != null) {
        try {
          met.setAccessible(true); // monstrous, monstrous
          met.invoke(obj, new Object[] { lockssContext });
        }
        catch (Exception exc) { throw failDeserialize(exc); }
      }
    }
    
    /**
     * <p>Looks up the hidden post-deserialization method in an
     * object.</p>
     * @param obj The object being considered.
     * @return A {@link Method} object reflecting the hidden
     *         post-deserialization method in the object's class (or
     *         one of its superclasses), or null if there is no such
     *         method in the object's inheritance hierarchy.
     */
    private Method lookupPostDeserializationMethod(Object obj) {
      Class objClass = obj.getClass();
      Object objMethod = methodCache.get(objClass);
      
      if (objMethod == null) { 
        return cachePostDeserializationMethod(obj); 
      }
      else if (objMethod == NONE) {
        return null; 
      }
      else {
        return (Method)objMethod; 
      }
    }
    
    /**
     * <p>A map to cache post-deserialization {@link Method}s by
     * class.</p>
     */
    private static final HashMap methodCache = new HashMap();
    
    /**
     * <p>A special unique value used in maps to denote that the
     * key exists but that it has no value.</p>
     */
    private static final Object NONE = new Object();
    
    /**
     * <p>The String name of the method automagically called during
     * post-deserialization of {@link LockssSerializable} objects.</p>
     * @see #POST_DESERIALIZATION_PARAMETERS
     */
    private static final String POST_DESERIALIZATION_METHOD =
      "postUnmarshal";
    
    /**
     * <p>The list of parameter types of the method automagically
     * called during post-deserialization of
     * {@link LockssSerializable} objects.</p>
     * @see #POST_DESERIALIZATION_METHOD
     */
    private static final Class[] POST_DESERIALIZATION_PARAMETERS =
      new Class[] { LockssApp.class };
    
  }
  /*
   * end PRIVATE STATIC INNER CLASS
   * ==============================
   */
  
  /**
   * <p>A lazy instantiation flag.</p>
   */
  private boolean initialized;
  
  /**
   * <p>A saved reference to the serialization context object.</p>
   */
  private LockssApp lockssContext;
  
  /**
   * <p>An instance of the {@link com.thoughtworks.xstream.XStream}
   * facade class.</p>
   */
  private XStream xs;

  /**
   * <p>Builds a new XStreamSerializer instance.</p>
   * <p>It is safe to use the same XStreamSerializer instance for
   * multiple unrelated marshalling and unmarshalling operations.</p>
   * <p>Uses a null context.</p>
   * @see #XStreamSerializer(org.lockss.app.LockssApp)
   */
  public XStreamSerializer() {
    this(null);
  }

  /**
   * <p>Builds a new XStreamSerializer instance.</p>
   * <p>It is safe to use the same XStreamSerializer instance for
   * multiple unrelated marshalling and unmarshalling operations.</p>
   * @param lockssContext A serialization context object.
   */
  public XStreamSerializer(LockssApp lockssContext) {
    super(lockssContext);
    this.initialized = false; // lazy instantiation, see init()
    this.lockssContext = lockssContext;
  }

  /* Inherit documentation */
  public Object deserialize(Reader reader)
      throws IOException, SerializationException {
    try {
      init(); // lazy instantiation
      return xs.fromXML(reader);
    }
    catch (StreamException se) {
      throw new IOException(se.getMessage());
    }
    catch (CannotResolveClassException crce) {
      throw failDeserialize(crce);
    }
    catch (BaseException be) {
      /*
       * Catches all others:
       * com.thoughtworks.xstream.converters.ConversionException
       * com.thoughtworks.xstream.converters.reflection.ObjectAccessException
       * com.thoughtworks.xstream.converters.reflection.ReflectionConverter.DuplicateFieldException
       */
      throw failDeserialize(be);
    }
  }

  /* Inherit documentation */
  protected void serialize(Writer writer, Object obj)
      throws IOException, SerializationException {
    throwIfNull(obj); 
    try {
      init();
      xs.toXML(obj, writer); // lazy instantiation
    }
    catch (LockssNotSerializableException inse) {
      throw new NotSerializableException();
    }
    catch (StreamException se) {
      throw new IOException(se.getMessage());
    }
    catch (CannotResolveClassException crce) {
      throw failSerialize(crce, obj);
    }
    catch (BaseException be) {
      /*
       * Catches all others:
       * com.thoughtworks.xstream.converters.ConversionException
       * com.thoughtworks.xstream.converters.reflection.ObjectAccessException
       * com.thoughtworks.xstream.converters.reflection.ReflectionConverter.DuplicateFieldException
       */
      throw failSerialize(be, obj);
    }
  }
  
  /**
   * <p>Performs tasks to resolve the lazy instantiation.</p>
   */
  private synchronized void init() {
    if (!initialized) {
      initialized = true;
      xs = new XStream(new DomDriver());
      xs.setMarshallingStrategy(
          new LockssReferenceByXPathMarshallingStrategy(lockssContext));
    }
  }
  
}
