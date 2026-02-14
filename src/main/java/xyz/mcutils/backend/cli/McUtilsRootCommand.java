package xyz.mcutils.backend.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import xyz.mcutils.backend.cli.impl.ResyncAccountsUsedCommand;

/**
 * Root CLI command for McUtils. Subcommands are invoked as the first argument
 * (e.g. {@code resync-accounts-used}).
 */
@Command(
        name = "mcutils",
        mixinStandardHelpOptions = true,
        subcommands = { ResyncAccountsUsedCommand.class }
)
@Component
public class McUtilsRootCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.err);
    }
}
