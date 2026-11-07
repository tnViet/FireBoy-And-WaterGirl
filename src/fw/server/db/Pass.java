package fw.server.db;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class Pass {
    private Pass() {}

    public static String hash(String pw) {
        return BCrypt.withDefaults().hashToString(10, pw.toCharArray());
    }

    public static boolean check(String pw, String hash) {
        return BCrypt.verifyer().verify(pw.toCharArray(), hash).verified;
    }
}
