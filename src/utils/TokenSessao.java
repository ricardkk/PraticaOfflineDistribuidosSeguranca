package utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TokenSessao {
    
    private static final String ALGORITMO = "HmacSHA256";
    private static final long TEMPO_EXPIRACAO_MS = 3600000;
    private static final String CHAVE_SECRETA;
    
    private static final Map<String, InfoToken> tokensAtivos = new ConcurrentHashMap<>();
    
    static {
        byte[] chaveBytes = new byte[32];
        new SecureRandom().nextBytes(chaveBytes);
        CHAVE_SECRETA = Base64.getEncoder().encodeToString(chaveBytes);
    }
    
    private static class InfoToken {
        String token;
        long timestampExpiracao;
        String clienteId;
        String clienteNome;
        
        InfoToken(String token, long timestampExpiracao, String clienteId, String clienteNome) {
            this.token = token;
            this.timestampExpiracao = timestampExpiracao;
            this.clienteId = clienteId;
            this.clienteNome = clienteNome;
        }
        
        boolean isValido() {
            return System.currentTimeMillis() < timestampExpiracao;
        }
    }
    
    public static String gerarToken(String clienteId, String clienteNome) {
        try {
            long agora = System.currentTimeMillis();
            long expiracao = agora + TEMPO_EXPIRACAO_MS;
            
            String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            
            String payload = String.format(
                "{\"sub\": \"%s\", \"name\": \"%s\", \"iat\": %d, \"exp\": %d}",
                clienteId, clienteNome, agora / 1000, expiracao / 1000
            );
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            
            String dadosParaAssinar = header + "." + payloadEncoded;
            String assinatura = gerarAssinatura(dadosParaAssinar);
            
            String token = dadosParaAssinar + "." + assinatura;
            
            tokensAtivos.put(clienteId, new InfoToken(token, expiracao, clienteId, clienteNome));
            
            return token;
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar token: " + e.getMessage());
        }
    }
    
    public static boolean validarToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        try {
            String[] partes = token.split("\\.");
            if (partes.length != 3) {
                return false;
            }
            
            String dadosParaAssinar = partes[0] + "." + partes[1];
            String assinaturaEsperada = gerarAssinatura(dadosParaAssinar);
            
            if (!assinaturaEsperada.equals(partes[2])) {
                return false;
            }
            
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(partes[1]), 
                StandardCharsets.UTF_8
            );
            
            String clienteId = extrairCampo(payloadJson, "sub");
            if (clienteId == null) {
                return false;
            }
            
            InfoToken info = tokensAtivos.get(clienteId);
            if (info == null || !info.token.equals(token)) {
                return false;
            }
            
            if (!info.isValido()) {
                tokensAtivos.remove(clienteId);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    public static String extrairClienteId(String token) {
        try {
            String[] partes = token.split("\\.");
            if (partes.length != 3) {
                return null;
            }
            
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(partes[1]), 
                StandardCharsets.UTF_8
            );
            
            return extrairCampo(payloadJson, "sub");
            
        } catch (Exception e) {
            return null;
        }
    }
    
    public static void revogarToken(String clienteId) {
        tokensAtivos.remove(clienteId);
    }
    
    public static void limparTokensExpirados() {
        tokensAtivos.entrySet().removeIf(entry -> !entry.getValue().isValido());
    }
    
    public static int getNumeroTokensAtivos() {
        limparTokensExpirados();
        return tokensAtivos.size();
    }
    
    private static String gerarAssinatura(String dados) throws Exception {
        Mac mac = Mac.getInstance(ALGORITMO);
        SecretKeySpec secretKey = new SecretKeySpec(
            CHAVE_SECRETA.getBytes(StandardCharsets.UTF_8), 
            ALGORITMO
        );
        mac.init(secretKey);
        byte[] assinatura = mac.doFinal(dados.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(assinatura);
    }
    
    private static String extrairCampo(String json, String campo) {
        String chave = "\"" + campo + "\"";
        int inicio = json.indexOf(chave);
        if (inicio == -1) return null;
        
        inicio = json.indexOf("\"", inicio + chave.length());
        if (inicio == -1) return null;
        
        int fim = json.indexOf("\"", inicio + 1);
        if (fim == -1) return null;
        
        return json.substring(inicio + 1, fim);
    }
}

