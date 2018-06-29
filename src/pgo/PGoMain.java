package pgo;

import org.apache.commons.io.FileUtils;
import pgo.errors.TopLevelIssueContext;
import pgo.formatters.GoNodeFormattingVisitor;
import pgo.formatters.IndentingWriter;
import pgo.model.golang.GoModule;
import pgo.model.pcal.PlusCalAlgorithm;
import pgo.model.tla.TLAExpression;
import pgo.model.tla.TLAModule;
import pgo.model.type.PGoType;
import pgo.modules.TLAModuleLoader;
import pgo.scope.UID;
import pgo.trans.PGoTransException;
import pgo.trans.intermediate.*;
import pgo.trans.passes.codegen.CodeGenPass;
import pgo.trans.passes.constdef.ConstantDefinitionParsingPass;
import pgo.trans.passes.tlaparse.TLAParsingPass;
import pgo.trans.passes.type.TypeInferencePass;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class PGoMain {
	private String[] cmdArgs;
	private static Logger logger;

	public PGoMain(String[] args) {
		cmdArgs = args;
	}

	// Creates a PGoMain instance, and initiates run() below.
	public static void main(String[] args) {
		// Get the top Logger instance
		logger = Logger.getLogger("PGoMain");
		if(new PGoMain(args).run()) {
			logger.info("Finished");
		}else {
			logger.info("Terminated with errors");
		}
	}

	// Top-level workhorse method.
	public boolean run() {
		try {
			TopLevelIssueContext ctx = new TopLevelIssueContext();

			// Check options, set up logging.
			PGoOptions opts = OptionParsingPass.perform(ctx, logger, cmdArgs);
			if (ctx.hasErrors()) {
				System.err.println(ctx.format());
				opts.printHelp();
				return false;
			}

			logger.info("Opening source file");
			Path inputFilePath = Paths.get(opts.inputFilePath);
			final PlusCalAlgorithm plusCalAlgorithm;
			final TLAModule tlaModule;
			try(FileChannel fileChannel = new RandomAccessFile(inputFilePath.toFile(), "r").getChannel()) {
				MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
				// assume UTF-8, though technically TLA+ is ASCII only according to the book
				CharBuffer inputFileContents = StandardCharsets.UTF_8.decode(buffer);

				logger.info("Parsing PlusCal code");
				plusCalAlgorithm = PlusCalParsingPass.perform(ctx, inputFilePath, inputFileContents);
				checkErrors(ctx);

				logger.info("Parsing TLA+ module");
				tlaModule = TLAParsingPass.perform(ctx, inputFilePath, inputFileContents);
				checkErrors(ctx);
			}

			logger.info("Parsing constant definitions from configuration");
			Map<String, TLAExpression> constantDefinitions = ConstantDefinitionParsingPass.perform(
					ctx, opts.constants.getConstants());
			checkErrors(ctx);

			logger.info("Checking compile options for sanity");
			CheckOptionsPass.perform(ctx, plusCalAlgorithm, opts);
			checkErrors(ctx);

			logger.info("Expanding PlusCal macros");
			final PlusCalAlgorithm macroExpandedPlusCalAlgorithm = PlusCalMacroExpansionPass.perform(
					ctx, plusCalAlgorithm);
			checkErrors(ctx);

			logger.info("Resolving TLA+ and PlusCal scoping");
			TLAModuleLoader loader = new TLAModuleLoader(Collections.singletonList(inputFilePath.getParent()));
			DefinitionRegistry registry = PGoScopingPass.perform(
					ctx, tlaModule, macroExpandedPlusCalAlgorithm, loader, constantDefinitions);
			checkErrors(ctx);

			logger.info("Inferring types");
			Map<UID, PGoType> typeMap = TypeInferencePass.perform(ctx, registry, macroExpandedPlusCalAlgorithm);
			checkErrors(ctx);

			logger.info("Inferring atomicity requirements");
			AtomicityInferencePass.perform(registry, macroExpandedPlusCalAlgorithm);

			logger.info("Initial code generation");
			GoModule goModule = CodeGenPass.perform(registry, typeMap, opts, macroExpandedPlusCalAlgorithm);

			logger.info("Normalising generated code");
			GoModule normalisedGoModule = CodeNormalisingPass.perform(goModule);

			logger.info("Writing Go module to \"" + opts.buildFile + "\" in folder \"" + opts.buildDir + "\"");
			try(
					BufferedWriter writer = Files.newBufferedWriter(Paths.get(opts.buildDir+"/"+opts.buildFile));
					IndentingWriter out = new IndentingWriter(writer)
			) {
				normalisedGoModule.accept(new GoNodeFormattingVisitor(out));
			}

			logger.info("Copying necessary Go packages to folder \"" + opts.buildDir + "\"");
			copyPackages(opts.buildDir);

			logger.info("Formatting generated Go code");
			try {
				goFmt(opts.buildDir + "/" + opts.buildFile);
			} catch (Exception e) {
				logger.warning(String.format("Failed to format Go code. Error: %s", e.getMessage()));
			}
			return true;

		} catch (PGoTransException | IOException e) {
			logger.severe(e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	private static void checkErrors(TopLevelIssueContext ctx) throws PGoTransException {
		if (ctx.hasErrors()) {
			throw new PGoTransException(ctx.format());
		}
	}

	private static void copyPackages(String buildDir) throws IOException {
		FileUtils.copyDirectory(new File("src/runtime/pgo"), new File(buildDir + "/src/pgo"));
	}

	private void goFmt(String... files) throws IOException, InterruptedException {
		String command = "gofmt -w " + String.join(" ", files);
		Process p = Runtime.getRuntime().exec(command);
		p.waitFor();
	}
}
