package bluej.debugger.jdi;

import java.io.*;

import bluej.debugger.DebuggerTestResult;

/**
 * Represents the result of running a single test method.
 */
public class JdiTestResult extends DebuggerTestResult
{
	private String className;
	private String methodName;
	private String exceptionMsg, traceMsg; 	// null if no failure

	JdiTestResult(String className, String methodName)
	{
		this(className, methodName, null, null);
	}

    JdiTestResult(String className, String methodName, String exceptionMsg, String traceMsg)
    {
        if (className == null || methodName == null)
            throw new NullPointerException("constructing JdiTestResult");

		this.className = className;
		this.methodName = methodName;

		this.exceptionMsg = exceptionMsg;

		if (traceMsg != null)
			this.traceMsg = getFilteredTrace(traceMsg);
	    else
	    	this.traceMsg = null;
    }
    
    /**
     * @see bluej.debugger.DebuggerTestResult#getExceptionMessage()
     */
    public String getExceptionMessage()
    {
        return exceptionMsg;
    }

    /**
     * 
     * 
     * @see bluej.debugger.DebuggerTestResult#getName()
     */
    public String getName()
    {
        return className + "." + methodName;
    }

    /**
     * 
     * @see bluej.debugger.DebuggerTestResult#getTrace()
     */
    public String getTrace()
    {
        return traceMsg;
    }

    /* (non-Javadoc)
     * @see bluej.debugger.DebuggerTestResult#isError()
     */
    public boolean isError()
    {
        return exceptionMsg != null;
    }

    /* (non-Javadoc)
     * @see bluej.debugger.DebuggerTestResult#isFailure()
     */
    public boolean isFailure()
    {
        return exceptionMsg != null;
    }

    /* (non-Javadoc)
     * @see bluej.debugger.DebuggerTestResult#isSuccess()
     */
    public boolean isSuccess()
    {
        return exceptionMsg == null;
    }

	/**
	 * Filters stack frames from internal JUnit classes
	 */
	public static String getFilteredTrace(String stack)
	{
		StringWriter sw= new StringWriter();
		PrintWriter pw= new PrintWriter(sw);
		StringReader sr= new StringReader(stack);
		BufferedReader br= new BufferedReader(sr);

		String line;
		try {
			while ((line= br.readLine()) != null) {
				if (!filterLine(line))
					pw.println(line);
			}
		} catch (Exception IOException) {
			return stack; // return the stack unfiltered
		}
		return sw.toString();
	}

	static boolean filterLine(String line)
	{
		String[] patterns= new String[] {
			"junit.framework.TestCase",
			"junit.framework.TestResult",
			"junit.framework.TestSuite",
			"junit.framework.Assert.", // don't filter AssertionFailure
			"junit.swingui.TestRunner",
			"junit.awtui.TestRunner",
			"junit.textui.TestRunner",
			"sun.reflect.",
			"bluej.",
			"java.lang.reflect.Method.invoke("
		};
		for (int i= 0; i < patterns.length; i++) {
			if (line.indexOf(patterns[i]) > 0)
				return true;
		}
		return false;
	}
}
