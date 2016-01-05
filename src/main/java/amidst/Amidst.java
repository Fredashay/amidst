package amidst;

import java.io.File;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

import amidst.documentation.AmidstThread;
import amidst.documentation.CalledByAny;
import amidst.documentation.CalledOnlyBy;
import amidst.documentation.NotThreadSafe;
import amidst.gui.crash.CrashWindow;
import amidst.logging.FileLogger;
import amidst.logging.Log;

@NotThreadSafe
public class Amidst {
	private static final String UNCAUGHT_EXCEPTION_ERROR_MESSAGE = "Amidst has encounted an uncaught exception on thread: ";

	@CalledOnlyBy(AmidstThread.STARTUP)
	public static void main(String args[]) {
		initUncaughtExceptionHandler();
		parseCommandLineArgumentsAndRun(args);
	}

	private static void initUncaughtExceptionHandler() {
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable e) {
				handleCrash(e, UNCAUGHT_EXCEPTION_ERROR_MESSAGE + thread);
			}
		});
	}

	private static void parseCommandLineArgumentsAndRun(String[] args) {
		AmidstMetaData metadata = createMetadata();
		CommandLineParameters parameters = new CommandLineParameters();
		CmdLineParser parser = new CmdLineParser(parameters, ParserProperties
				.defaults().withShowDefaults(false).withUsageWidth(120)
				.withOptionSorter(null));
		try {
			parser.parseArgument(args);
			run(metadata, parameters, parser);
		} catch (CmdLineException e) {
			printLongVersionString(metadata);
			System.err.println(e.getMessage());
			parser.printUsage(System.out);
			System.exit(2);
		}
	}

	private static AmidstMetaData createMetadata() {
		return AmidstMetaData.from(
				ResourceLoader.getProperties("/amidst/metadata.properties"),
				ResourceLoader.getImage("/amidst/icon.png"));
	}

	private static void run(AmidstMetaData metadata,
			CommandLineParameters parameters, CmdLineParser parser) {
		initFileLogger(parameters.logFile);
		if (parameters.printHelp) {
			printLongVersionString(metadata);
			parser.printUsage(System.out);
		} else if (parameters.printVersion) {
			printLongVersionString(metadata);
		} else {
			startApplication(parameters, metadata);
		}
	}

	private static void printLongVersionString(AmidstMetaData metadata) {
		System.out.println(metadata.getVersion().createLongVersionString());
	}

	private static void initFileLogger(String filename) {
		if (filename != null) {
			Log.addListener("file", new FileLogger(new File(filename)));
		}
	}

	private static void startApplication(CommandLineParameters parameters,
			AmidstMetaData metadata) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				initGui();
				doStartApplication(parameters, metadata);
			}
		});
	}

	private static void initGui() {
		initLookAndFeel();
		setJava2DEnvironmentVariables();
	}

	private static void initLookAndFeel() {
		if (!isOSX()) {
			try {
				UIManager.setLookAndFeel(UIManager
						.getSystemLookAndFeelClassName());
			} catch (ClassNotFoundException | InstantiationException
					| IllegalAccessException | UnsupportedLookAndFeelException e) {
				Log.printTraceStack(e);
			}
		}
	}

	private static boolean isOSX() {
		return System.getProperty("os.name").contains("OS X");
	}

	private static void setJava2DEnvironmentVariables() {
		System.setProperty("sun.java2d.opengl", "True");
		System.setProperty("sun.java2d.accthreshold", "0");
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private static void doStartApplication(CommandLineParameters parameters,
			AmidstMetaData metadata) {
		try {
			new Application(parameters, metadata).run();
		} catch (Exception e) {
			handleCrash(e, "Amidst crashed!");
		}
	}

	@CalledByAny
	private static void handleCrash(Throwable e, String message) {
		try {
			Log.crash(e, message);
			displayCrashWindow(message, Log.getAllMessages());
		} catch (Throwable t) {
			System.err.println("Amidst crashed!");
			System.err.println(message);
			e.printStackTrace();
		}
	}

	private static void displayCrashWindow(final String message,
			final String allMessages) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new CrashWindow(message, allMessages, new Runnable() {
					@Override
					public void run() {
						System.exit(4);
					}
				});
			}
		});
	}
}
