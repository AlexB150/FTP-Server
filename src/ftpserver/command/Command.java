package ftpserver.command;

import java.io.IOException;

public class Command {

    private final CommandFunction cmd;
    private final String helpInfo;
    private final boolean needAuth;

    public Command(CommandFunction cmd, String helpInfo, boolean needAuth) {
        this.cmd = cmd;
        this.helpInfo = helpInfo;
        this.needAuth = needAuth;
    }

    public CommandFunction getCmd() {
        return cmd;
    }

    public String getHelpInfo() {
        return helpInfo;
    }

    public boolean needAuthentication() {
        return needAuth;
    }

    @FunctionalInterface
    public interface CommandFunction {
        void run (String argument) throws IOException;
    }

    @FunctionalInterface
    public interface CommandFunctionNoArgs extends CommandFunction {
        void run() throws IOException;

        @Override
        default void run(String argument) throws IOException {
            run();
        }
    }

    @FunctionalInterface
    public interface CommandFunctionMultiArgs extends CommandFunction {

        void run(String[] args) throws IOException;

        @Override
        default void run(String argument) throws IOException {
            run(argument.split("\\s+"));
        }
    }

}
