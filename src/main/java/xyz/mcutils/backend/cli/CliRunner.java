package xyz.mcutils.backend.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Runs CLI subcommands via Picocli when the application is started with
 * arguments (CLI mode). No-op when started with no arguments (server mode).
 */
@Component
public class CliRunner implements CommandLineRunner, ExitCodeGenerator {

    private final McUtilsRootCommand rootCommand;
    private final IFactory factory;
    private int exitCode = 0;

    public CliRunner(McUtilsRootCommand rootCommand, IFactory factory) {
        this.rootCommand = rootCommand;
        this.factory = factory;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            return;
        }
        exitCode = new CommandLine(rootCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
