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
Filename: CWE690_NULL_Deref_From_Return__System_getProperty_equals_51b.java
Label Definition File: CWE690_NULL_Deref_From_Return.label.xml
Template File: sources-sinks-51b.tmpl.java
*/
/*
 * @description
 * CWE: 690 Unchecked return value is null, leading to a null pointer dereference.
 * BadSource: System_getProperty Set data to return of System.getProperty
 * GoodSource: Set data to fixed, non-null String
 * Sinks: equals
 *    GoodSink: Call equals() on string literal (that is not null)
 *    BadSink : Call equals() on possibly null object
 * Flow Variant: 51 Data flow: data passed as an argument from one function to another in different classes in the same package
 *
 * */

package juliet.testcases.CWE690_NULL_Deref_From_Return;

import juliet.testcasesupport.IO;

public class CWE690_NULL_Deref_From_Return__System_getProperty_equals_51b
{
    public void badSink(String data ) throws Throwable
    {

        /* POTENTIAL FLAW: data could be null */
        if(data.equals("CWE690"))
        {
            IO.writeLine("data is CWE690");
        }

    }

    /* goodG2B() - use goodsource and badsink */
    public void goodG2BSink(String data ) throws Throwable
    {

        /* POTENTIAL FLAW: data could be null */
        if(data.equals("CWE690"))
        {
            IO.writeLine("data is CWE690");
        }

    }

    /* goodB2G() - use badsource and goodsink */
    public void goodB2GSink(String data ) throws Throwable
    {

        /* FIX: call equals() on string literal (that is not null) */
        if("CWE690".equals(data))
        {
            IO.writeLine("data is CWE690");
        }

    }
}
