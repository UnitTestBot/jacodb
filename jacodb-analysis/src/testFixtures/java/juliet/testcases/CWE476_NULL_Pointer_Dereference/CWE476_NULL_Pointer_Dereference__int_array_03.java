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

/* TEMPLATE GENERATED TESTCASE FILE
Filename: CWE476_NULL_Pointer_Dereference__int_array_03.java
Label Definition File: CWE476_NULL_Pointer_Dereference.label.xml
Template File: sources-sinks-03.tmpl.java
*/
/*
* @description
* CWE: 476 Null Pointer Dereference
* BadSource:  Set data to null
* GoodSource: Set data to a non-null value
* Sinks:
*    GoodSink: add check to prevent possibility of null dereference
*    BadSink : possibility of null dereference
* Flow Variant: 03 Control flow: if(5==5) and if(5!=5)
*
* */

package juliet.testcases.CWE476_NULL_Pointer_Dereference;

import juliet.testcasesupport.*;

public class CWE476_NULL_Pointer_Dereference__int_array_03 extends AbstractTestCase
{
    public void bad() throws Throwable
    {
        int [] data;
        if (5==5)
        {
            /* POTENTIAL FLAW: data is null */
            data = null;
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        if (5==5)
        {
            /* POTENTIAL FLAW: null dereference will occur if data is null */
            IO.writeLine("" + data.length);
        }
    }

    /* goodG2B1() - use goodsource and badsink by changing first 5==5 to 5!=5 */
    private void goodG2B1() throws Throwable
    {
        int [] data;
        if (5!=5)
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }
        else
        {

            /* FIX: hardcode data to non-null */
            data = new int[5];

        }

        if (5==5)
        {
            /* POTENTIAL FLAW: null dereference will occur if data is null */
            IO.writeLine("" + data.length);
        }
    }

    /* goodG2B2() - use goodsource and badsink by reversing statements in first if */
    private void goodG2B2() throws Throwable
    {
        int [] data;
        if (5==5)
        {
            /* FIX: hardcode data to non-null */
            data = new int[5];
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        if (5==5)
        {
            /* POTENTIAL FLAW: null dereference will occur if data is null */
            IO.writeLine("" + data.length);
        }
    }

    /* goodB2G1() - use badsource and goodsink by changing second 5==5 to 5!=5 */
    private void goodB2G1() throws Throwable
    {
        int [] data;
        if (5==5)
        {
            /* POTENTIAL FLAW: data is null */
            data = null;
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        if (5!=5)
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run */
            IO.writeLine("Benign, fixed string");
        }
        else
        {

            /* FIX: validate that data is non-null */
            if (data != null)
            {
                IO.writeLine("" + data.length);
            }
            else
            {
                IO.writeLine("data is null");
            }

        }
    }

    /* goodB2G2() - use badsource and goodsink by reversing statements in second if  */
    private void goodB2G2() throws Throwable
    {
        int [] data;
        if (5==5)
        {
            /* POTENTIAL FLAW: data is null */
            data = null;
        }
        else
        {
            /* INCIDENTAL: CWE 561 Dead Code, the code below will never run
             * but ensure data is inititialized before the Sink to avoid compiler errors */
            data = null;
        }

        if (5==5)
        {
            /* FIX: validate that data is non-null */
            if (data != null)
            {
                IO.writeLine("" + data.length);
            }
            else
            {
                IO.writeLine("data is null");
            }
        }
    }

    public void good() throws Throwable
    {
        goodG2B1();
        goodG2B2();
        goodB2G1();
        goodB2G2();
    }

    /* Below is the main(). It is only used when building this testcase on
     * its own for testing or for building a binary to use in testing binary
     * analysis tools. It is not used when compiling all the testcases as one
     * application, which is how source code analysis tools are tested.
     */
    public static void main(String[] args) throws ClassNotFoundException,
           InstantiationException, IllegalAccessException
    {
        mainFromParent(args);
    }
}
