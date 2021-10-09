package ftpserver.command;

import java.io.IOException;

public class TransferException extends IOException {

    private int responseCode;

    public TransferException(int code, String message) {
        super(message);
        responseCode = code;
    }

    public int getResponseCode(){
        return responseCode;
    }
}
