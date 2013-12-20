package junitconverter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import junitconverter.stages.AnnotationsImportingStage;
import junitconverter.stages.AssertsImportingStage;
import junitconverter.stages.PreperationMethodsAnnotationStage;
import junitconverter.stages.SuiteAnnotationsImportingStage;
import junitconverter.stages.SuiteRemovingStage;
import junitconverter.stages.SuiteRewritingStage;
import junitconverter.stages.SuperRemovingStage;
import junitconverter.stages.TestConversionStage;
import junitconverter.stages.TestMethodsAnnotationStage;
import junitconverter.stages.VisibilityAdaptionStage;
import junitconverter.testcase.SetUpMethod;
import junitconverter.testcase.TearDownMethod;
import junitconverter.testcase.TestCaseClass;
import junitconverter.testcase.TestMethod;

import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

public class TestClassConverter
{

	private static final String PUBLIC = "public"; //$NON-NLS-1$
	private static final String SUITE = "suite"; //$NON-NLS-1$
	private static final String FILENAME_SUFFIX = ".java"; //$NON-NLS-1$
	private static final String TEAR_DOWN = "tearDown"; //$NON-NLS-1$
	private static final String SET_UP = "setUp"; //$NON-NLS-1$
	private static final String TEST_METHOD_PREFIX = "test"; //$NON-NLS-1$

	private final List<TestConversionStage> stages = new ArrayList<TestConversionStage>();
	private final List<TestConversionStage> suiteStages = new ArrayList<TestConversionStage>();

	public TestClassConverter()
	{
		stages.add(new SuperRemovingStage());
		stages.add(new VisibilityAdaptionStage());
		stages.add(new PreperationMethodsAnnotationStage());
		stages.add(new TestMethodsAnnotationStage());
		stages.add(new AssertsImportingStage());
		stages.add(new AnnotationsImportingStage());
		suiteStages.add(new SuperRemovingStage());
		suiteStages.add(new SuiteRewritingStage());
		suiteStages.add(new SuiteAnnotationsImportingStage());
		suiteStages.add(new SuiteRemovingStage());
		// TODO Add stage to remove unused imports? Such as:
		// import junit.framework.Test;
		// import junit.framework.TestCase;
		// import junit.framework.TestResult;
		// import junit.framework.TestSuite;
	}

	public void convert(File inputFile, File outputFile) throws IOException, RecognitionException
	{
		JavaParser parser = new JavaParser(new CommonTokenStream(new JavaLexer(new ANTLRFileStream(
				inputFile.getAbsolutePath()))));
		parser.compilationUnit();

		if (isTestSuite(parser, inputFile))
		{
			writeChanges(outputFile, runSuiteConversion(inputFile, parser));
		}
		else if (isTestCase(parser, inputFile))
		{
			writeChanges(outputFile, runConversion(inputFile, parser));
		}
	}

	private List<String> runSuiteConversion(File inputFile, JavaParser parser) throws FileNotFoundException,
			IOException
	{
		List<String> lines = readLines(inputFile);

		TestCaseClass testCaseClass = buildTestCaseClass(parser, lines);

		ClassWriter classWriter = new SimpleClassWriter(lines);
		CodeEditor codeEditor = new SimpleCoderEditor(classWriter);

		for (TestConversionStage stage : suiteStages)
		{
			stage.setCodeEditor(codeEditor);
		}

		for (TestConversionStage stage : suiteStages)
		{
			stage.convertClass(testCaseClass);
		}

		return classWriter.result();
	}

	private boolean isTestSuite(JavaParser parser, File inputFile)
	{
		Set<String> methods = parser.getMethods();
		if (!methods.contains(SUITE))
		{
			return false;
		}
		String visibility = parser.getVisibility(SUITE);
		if (!PUBLIC.equals(visibility))
		{
			return false;
		}
		return true;
		// FIXME This seems to fail for me. We want to check that the method is static, but classloading fails. May be
		// an OSGi/Eclipse thing
		// try
		// {
		// Method m = Class.forName(parser.getFullName()).getMethod(SUITE);
		// return Modifier.isStatic(m.getModifiers());
		// }
		// catch (Exception e)
		// {
		// return false;
		// }
	}

	private void writeChanges(File outputFile, List<String> newLines) throws IOException
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		try
		{
			for (String line : newLines)
			{
				writer.write(line);
				writer.write("\n");
			}
		}
		finally
		{
			try
			{
				writer.close();
			}
			catch (IOException e)
			{
				// Ignore
			}
		}
	}

	private List<String> runConversion(File inputFile, JavaParser parser) throws FileNotFoundException, IOException
	{
		List<String> lines = readLines(inputFile);

		TestCaseClass testCaseClass = buildTestCaseClass(parser, lines);

		ClassWriter classWriter = new SimpleClassWriter(lines);
		CodeEditor codeEditor = new SimpleCoderEditor(classWriter);

		for (TestConversionStage stage : stages)
		{
			stage.setCodeEditor(codeEditor);
		}

		for (TestConversionStage stage : stages)
		{
			stage.convertClass(testCaseClass);
		}

		return classWriter.result();
	}

	private boolean isTestCase(JavaParser parser, File inputFile)
	{
		if (!TestCase.class.getSimpleName().equals(parser.getSuperName())
				&& !TestCase.class.getName().equals(parser.getSuperName()))
		{
			try
			{
				if (!TestCase.class.isAssignableFrom(Class.forName(parser.getFullName())))
				{
					return false;
				}
			}
			catch (ClassNotFoundException e)
			{
				return false;
			}
		}

		return true;
	}

	private TestCaseClass buildTestCaseClass(JavaParser parser, List<String> lines)
	{
		SetUpMethod setUpMethod = null;
		if (parser.getMethodsWithLines().containsKey(SET_UP))
		{
			setUpMethod = new SetUpMethod(parser.getMethodsWithLines().get(SET_UP), parser.getAnnotations(SET_UP));
		}

		TearDownMethod tearDownMethod = null;
		if (parser.getMethodsWithLines().containsKey(TEAR_DOWN))
		{
			tearDownMethod = new TearDownMethod(parser.getMethodsWithLines().get(TEAR_DOWN),
					parser.getAnnotations(TEAR_DOWN));
		}

		List<TestMethod> testMethods = new ArrayList<TestMethod>();
		for (String methodName : parser.getMethods())
		{
			if (methodName.startsWith(TEST_METHOD_PREFIX) && isVisibleEnough(parser, methodName))
			{
				testMethods.add(new TestMethod(parser.getMethodsWithLines().get(methodName), parser
						.getAnnotations(methodName)));
			}
		}

		String superName = parser.getSuperName();
		TestCaseClass testCaseClass = new TestCaseClass(lines, setUpMethod, tearDownMethod, testMethods, superName,
				parser.getAnnotations("")); // Hack, we use empty string to grab the annotations for the class

		Integer suiteLine = parser.getMethodsWithLines().get(SUITE);
		if (suiteLine != null)
		{
			// FIXME This is commenting too far into class
			int endLine = lines.size() - 2;
			for (Map.Entry<String, Integer> entry : parser.getMethodsWithLines().entrySet())
			{
				if (entry.getValue() > suiteLine)
				{
					endLine = Math.min(endLine, entry.getValue() - 2);
				}
			}
			testCaseClass.setSuiteLine(suiteLine - 1, endLine);
		}
		testCaseClass.setExtendsLine(parser.getSuperLine());
		testCaseClass.setExtendsLine(parser.getTypeLine());
		testCaseClass.setOverrideAnnotationsLines(new LinkedList<Integer>(parser.getOverrideAnnotationsLines()));
		testCaseClass.setSuperConstructorInvocations(new LinkedList<Integer>(parser.getSuperConstructorInvocations()));
		testCaseClass.setSuperMethodInvocations(new LinkedList<Integer>(parser.getSuperMethodInvocations()));
		return testCaseClass;
	}

	private boolean isVisibleEnough(JavaParser parser, String methodName)
	{
		return (parser.getVisibility(methodName).equals(Visibility.PUBLIC.toString()) || parser.getVisibility(
				methodName).equals(Visibility.PROTECTED.toString()));
	}

	private List<String> readLines(File inputFile) throws FileNotFoundException, IOException
	{
		List<String> lines = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		try
		{
			while (reader.ready())
			{
				lines.add(reader.readLine());
			}
		}
		finally
		{
			try
			{
				reader.close();
			}
			catch (IOException e)
			{
				// Ignore
			}
		}
		return lines;
	}

	private static void listJavaFilesRecursively(File dir, List<File> files)
	{
		for (File file : dir.listFiles())
		{
			if (file.isDirectory())
			{
				listJavaFilesRecursively(file, files);
			}
			else
			{
				if (file.getName().endsWith(FILENAME_SUFFIX))
				{
					files.add(file);
				}
			}
		}
	}

	public static void main(String[] args) throws IOException, RecognitionException
	{

		if (args.length != 1)
		{
			usage();
			return;
		}

		File rootFile = new File(args[0]);

		List<File> files = new LinkedList<File>();
		if (rootFile.isDirectory())
		{
			listJavaFilesRecursively(rootFile, files);
		}
		else
		{
			files.add(rootFile);
		}
		TestClassConverter testClassConverter = new TestClassConverter();
		for (File file : files)
		{
			testClassConverter.convert(file, file);
		}
	}

	private static void usage()
	{
		System.err.println("Usage: java " + TestClassConverter.class.getName() + " <src dir>");
		System.exit(1);
	}
}
