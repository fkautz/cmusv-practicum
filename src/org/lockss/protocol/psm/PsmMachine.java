/*
* $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.protocol.psm;

import java.util.*;
import org.lockss.util.*;

/**
 * Defines the states, actions and transitions in a state machine.  Holds
 * no state.
 */
public class PsmMachine {

  private String name;
  private PsmState initialState;
  private PsmState[] states;
  private Map stateMap;

  private PsmMachine(String name, PsmState[] states) {
    this.name = name;
    this.states = states;
  }

  /** Create a state machine.
   * @param name the machines's name
   * @param states array of states
   * @param initialState start state
   */
  public PsmMachine(String name, PsmState[] states, PsmState initialState) {
    this(name, states);
    this.initialState = initialState;
    validate();
  }

  /** Create a state machine.
   * @param name the machines's name
   * @param states array of states
   * @param initialStateName name of start state
   */
  public PsmMachine(String name, PsmState[] states, String initialStateName) {
    this(name, states);
    this.initialState = getState(initialStateName);
    validate();
  }

  private void validate() {
    if (states == null)
      throw new PsmException.IllegalStateMachine("states is null");
    if (name == null)
      throw new PsmException.IllegalStateMachine("name is null");
    if (initialState == null)
      throw new PsmException.IllegalStateMachine("Initial state is null");
    buildStateMap();
    if (getState(initialState.getName()) != initialState)
      throw new PsmException.IllegalStateMachine("Initial state not in states");
    // validate transitions
  }

  /** Return the name of the state machine */
  public String getName() {
    return name;
  }

  /** Return the initial state of the state machine */
  public PsmState getInitialState() {
    return initialState;
  }

  /** Return the state array of the state machine */
  public PsmState[] getStates() {
    return states;
  }

  /** Find the state with the given name */
  public PsmState getState(String name) {
    if (stateMap == null) {
      buildStateMap();
    }
    return (PsmState)stateMap.get(name);
  }

  private synchronized void buildStateMap() {
    if (stateMap == null) {
      Map newmap = new HashMap();;
      for (int ix = 0; ix < states.length; ix++) {
	PsmState state = states[ix];
	if (newmap.put(state.getName(), state) != null) {
	  throw new PsmException.IllegalStateMachine("Two or more states named " +
				     state.getName());
	}
      }
      stateMap = newmap;
    }
  }

  // prototype for List.toArray() calls
  static final PsmState[] EMPTY_PSM_STATE_ARRAY = new PsmState[0];

  PsmState[] stateArray(List lst) {
    return (PsmState[])lst.toArray(EMPTY_PSM_STATE_ARRAY);
  }
}
