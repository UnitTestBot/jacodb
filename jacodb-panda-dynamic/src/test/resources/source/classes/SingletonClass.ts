class Logger {
    private static instance: Logger;

    private constructor() {
    }

    public static getInstance(): Logger {
        if (!Logger.instance) {
            Logger.instance = new Logger();
        }
        return Logger.instance;
    }

    public log(message: string): void {
        console.log(message);
    }
}

const logger = Logger.getInstance();
logger.log("This is a log message.");
