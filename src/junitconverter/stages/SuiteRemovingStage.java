package junitconverter.stages;

import junitconverter.testcase.TestCaseClass;

/**
 * Removes the suite() method from test suite classes.
 * 
 * @author cwilliams
 */
public class SuiteRemovingStage extends AbstractTestConversionStage
{

	/**
	 * @see junitconverter.stages.TestConversionStage#convertClass(junitconverter.testcase.TestCaseClass)
	 */
	public void convertClass(TestCaseClass testCase)
	{
		codeEditor.removeSuite(testCase);
	}
}
