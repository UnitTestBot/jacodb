/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
@description This abstract class is the base for the other 
AbstractTestCaseServlet classes.

*/
/* note on servlets - when a new Servlet is created, it must be listed in the web.xml
 * file in order to run.  The easy way to do this is in Eclipse:
 *   - right click on testcases in package explorer
 *   - select New->Servlet
 *   - Check the box that says use an existing Servlet class or JSP
 *   - Click browse and select the new Servlet class you have created
 *   - Click OK, Next, then Finish  
 *   - You should now be able to test the servlet by clicking Run in eclipse  
 */

package juliet.testcasesupport;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class AbstractTestCaseServletBase extends HttpServlet 
{
    private static final long serialVersionUID = 1L; /* needed since Servlets are serializable */

    /* from a static method like main(), there is not an easy way to get the current
     * classes's name.  We do a trick here to make it work so that we don't have
     * to edit the main for each test case or use a string member to contain the class
     * name
     */
    public static void mainFromParent(String[] args)
        throws ClassNotFoundException, InstantiationException, IllegalAccessException 
    {
        StackTraceElement stackTraceElements[] = Thread.currentThread().getStackTrace();
    
        String myClassName = stackTraceElements[stackTraceElements.length -1].getClassName();
    
        Class<?> myClass = Class.forName(myClassName);
    
        AbstractTestCase myObject = (AbstractTestCase) myClass.newInstance();
        
        myObject.runTest(myClassName);
    }

    /* this method runs a test on its own, creating a full HTML document
     * it is called from doGet and doPost in this file
     */
    public void runTestSolo(HttpServletRequest request, HttpServletResponse response)
        throws IOException 
    {  
        response.getWriter().println("<html><body>");
        
        runTest(request, response);

        response.getWriter().println("</body></html>");   
    }
     
    /* this method runs the tests, but assumes that the html document has already
     * been started.  It is called by runTestSolo and by ServletMain
     */
    abstract public void runTest(HttpServletRequest request, HttpServletResponse response) throws IOException;

    public void doPost(HttpServletRequest request, HttpServletResponse response) 
    {
        doGet(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) 
    {
        try 
        {
            runTestSolo(request, response);
        } 
        catch (Throwable throwableException) 
        {
            IO.writeLine("Caught a throwable from runTest()");

            IO.writeLine("<br>Throwable's message = " + throwableException.getMessage());

            StackTraceElement stackTraceElements[] = throwableException.getStackTrace();

            IO.writeLine("Stack trace below");

            for (StackTraceElement stackTraceElement : stackTraceElements) 
            {
                IO.writeLine(stackTraceElement.toString());
            } 
        } 
    }
} 
