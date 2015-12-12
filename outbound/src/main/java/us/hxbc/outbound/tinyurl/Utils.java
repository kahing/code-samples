package us.hxbc.outbound.tinyurl;

import org.sqlite.SQLiteErrorCode;

import java.sql.SQLException;
import java.util.Random;

public class Utils {
    static String randomString(int len) {
        Random r = new Random();
        StringBuffer buf = new StringBuffer(len);
        for (int i = 0; i < len; i++) {
            int nextChar = r.nextInt(26);
            buf.append((char)('a' + nextChar));
        }
        return buf.toString();
    }

    static int sqlExceptionGetCode(SQLException e) {
        // for some reason e.getErrorCode() == 0
        if (e.getMessage().indexOf("SQLITE_CONSTRAINT") != -1) {
            return SQLiteErrorCode.SQLITE_CONSTRAINT.code;
        }

        return SQLiteErrorCode.UNKNOWN_ERROR.code;
    }
}
