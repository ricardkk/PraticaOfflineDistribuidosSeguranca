package utils;

public class Cores {
    
    public static final String RESET = "\u001B[0m";
    public static final String VERMELHO = "\u001B[31m";
    public static final String VERDE = "\u001B[32m";
    public static final String AMARELO = "\u001B[33m";
    public static final String AZUL = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CIANO = "\u001B[36m";
    public static final String BRANCO = "\u001B[37m";
    
    public static final String VERMELHO_BOLD = "\u001B[1;31m";
    public static final String VERDE_BOLD = "\u001B[1;32m";
    public static final String AMARELO_BOLD = "\u001B[1;33m";
    public static final String AZUL_BOLD = "\u001B[1;34m";
    public static final String MAGENTA_BOLD = "\u001B[1;35m";
    public static final String CIANO_BOLD = "\u001B[1;36m";
    
    public static final String LARANJA = "\u001B[38;5;208m";
    public static final String LARANJA_BOLD = "\u001B[1;38;5;208m";
    
    public static final String BG_VERMELHO = "\u001B[41m";
    public static final String BG_AMARELO = "\u001B[43m";
    public static final String BG_VERDE = "\u001B[42m";
    
    public static String vermelho(String texto) {
        return VERMELHO + texto + RESET;
    }
    
    public static String verde(String texto) {
        return VERDE + texto + RESET;
    }
    
    public static String amarelo(String texto) {
        return AMARELO + texto + RESET;
    }
    
    public static String azul(String texto) {
        return AZUL + texto + RESET;
    }
    
    public static String laranja(String texto) {
        return LARANJA + texto + RESET;
    }
    
    public static String laranjaBold(String texto) {
        return LARANJA_BOLD + texto + RESET;
    }
    
    public static String vermelhoBold(String texto) {
        return VERMELHO_BOLD + texto + RESET;
    }
    
    public static String ciano(String texto) {
        return CIANO + texto + RESET;
    }
    
    public static String magenta(String texto) {
        return MAGENTA + texto + RESET;
    }
    
    public static String bytesParaHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) return "";
        
        StringBuilder hex = new StringBuilder();
        int limite = Math.min(bytes.length, maxBytes);
        
        for (int i = 0; i < limite; i++) {
            hex.append(String.format("%02X", bytes[i]));
        }
        
        if (bytes.length > maxBytes) {
            hex.append("... (").append(bytes.length).append(" bytes total)");
        }
        
        return hex.toString();
    }
}

