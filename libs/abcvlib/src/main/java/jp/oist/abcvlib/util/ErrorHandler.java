package jp.oist.abcvlib.util;

import jp.oist.abcvlib.util.Logger;

public class ErrorHandler {
    public static void eLog(String TAG, String comment, Exception e, boolean crash){
        Logger.e(TAG, comment, e);
        if (crash){
            throw new RuntimeException("This is an intentional debugging crash");
        }
    }
}
