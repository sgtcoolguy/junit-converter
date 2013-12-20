package junitconverter.stages;

import junitconverter.testcase.TestCaseClass;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * This stage is responsible for adding imports for the {@link Suite}, {@link SuiteClasses} and {@link RunWith}
 * annotations.
 * 
 * @author cwilliams
 */
public class SuiteAnnotationsImportingStage extends AbstractTestConversionStage
{

	/**
	 * @see junitconverter.stages.TestConversionStage#convertClass(junitconverter.testcase.TestCaseClass)
	 */
	public void convertClass(TestCaseClass testCase)
	{
		codeEditor.importClass(testCase, Suite.class);
		codeEditor.importClass(testCase, RunWith.class);
		codeEditor.importClass(testCase, SuiteClasses.class); // FIXME Doesn't import it properly. Adds a $ instead of . between SUite and SuiteClasses
	}

}
