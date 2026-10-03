package g_mungus.alpha_omega.wrap;

/**
 * Duck interface for vanilla classes shared by client and server where only the server-side instances store
 * canonical positions. The client works entirely in its own unrolled frame and needs no canonicalization.
 */
public interface WrapFlag {

    boolean alpha_omega$isWrapped();

    void alpha_omega$setWrapped(boolean wrapped);
}
