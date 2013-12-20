package junitconverter.stages;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junitconverter.testcase.TestCaseClass;

import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

/**
 * This stage is responsible for rewriting test suites using new JUnit 4 syntax and annotatons.
 * 
 * @author abyx
 */
public class SuiteRewritingStage extends AbstractTestConversionStage
{
	// This lovely regex matches addTest and addTestSuite calls so we can extract out the classes/suites added
	private static final Pattern p = Pattern
			.compile("\\.addTest(Suite)?\\s*\\((([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)\\.(suite\\(\\)|class)\\s*\\);");

	/**
	 * @see junitconverter.stages.TestConversionStage#convertClass(junitconverter.testcase.TestCaseClass)
	 */
	public void convertClass(TestCaseClass testCase)
	{
		// This is a gigantic hack. We use a regex to search for classes/suites added to the suite.
		// We start our search at the beginning of the suite method
		List<String> lines = testCase.getLines().subList(testCase.getSuiteStartLine(), testCase.getSuiteEndLine() + 1);
		StringBuilder builder = new StringBuilder();
		builder.append('{');
		for (String line : lines)
		{
			Matcher m = p.matcher(line);
			if (m.find())
			{
				String testName = m.group(2);
				builder.append(testName).append(".class, "); //$NON-NLS-1$
			}
		}
		builder.append('}');
		codeEditor.addAnnotation(testCase, SuiteClasses.class, builder.toString());
		codeEditor.addAnnotation(testCase, RunWith.class, "Suite.class"); //$NON-NLS-1$
	}
}
