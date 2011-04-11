package org.lockss.prototypes;

import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;

public class LinkExtractionUsingSelenium  {
    public static void main(String[] args) {
        // Create a new instance of the Firefox driver
        // Notice that the remainder of the code relies on the interface, 
        // not the implementation.
    	Proxy p = new Proxy();
    	p.setHttpProxy("127.0.0.1:8081");
    	p.setSslProxy("127.0.0.1:8081");
        WebDriver driver = new FirefoxDriver(new FirefoxProfile().setProxyPreferences(p));
        
        Proxy p2 = new Proxy();
        p2.setHttpProxy("127.0.0.1:8082");
        p2.setSslProxy("127.0.0.1:8082");
        WebDriver driver2 = new FirefoxDriver(new FirefoxProfile().setProxyPreferences(p2));

        // And now use this to visit Google
        driver.get("http://www.google.com");
        driver2.get("http://www.yahoo.com");

        // Find the text input element by its name
        WebElement element = driver.findElement(By.name("q"));

        // Enter something to search for
        element.sendKeys("Cheese!");

        // Now submit the form. WebDriver will find the form for us from the element
        element.submit();

        // Check the title of the page
        System.out.println("Page title is: " + driver.getTitle());
        
        //Close the browser
        driver.quit();
    }
}