package utils;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ServicoSeguranca {
    
    private static final String ALGORITMO_RSA = "RSA/ECB/PKCS1Padding";
    private static final String ALGORITMO_AES = "AES/ECB/PKCS5Padding";
    private static final int TAMANHO_CHAVE_RSA = 2048;
    private static final int TAMANHO_CHAVE_AES = 256;
    
    public static KeyPair gerarParChavesRSA() throws NoSuchAlgorithmException {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(TAMANHO_CHAVE_RSA);
        return gerador.generateKeyPair();
    }
    
    public static KeyPair gerarParChavesRSADeterministico(String semente) throws NoSuchAlgorithmException {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(semente.getBytes());
        gerador.initialize(TAMANHO_CHAVE_RSA, random);
        return gerador.generateKeyPair();
    }
    
    public static SecretKey gerarChaveAES() throws NoSuchAlgorithmException {
        KeyGenerator gerador = KeyGenerator.getInstance("AES");
        gerador.init(TAMANHO_CHAVE_AES);
        return gerador.generateKey();
    }
    
    public static SecretKey gerarChaveAESSegura() throws NoSuchAlgorithmException {
        KeyGenerator gerador = KeyGenerator.getInstance("AES");
        SecureRandom random = new SecureRandom();
        gerador.init(TAMANHO_CHAVE_AES, random);
        return gerador.generateKey();
    }
    
    public static byte[] criptografar(byte[] dados, PublicKey chavePublicaDestinatario) throws Exception {
        SecretKey chaveAES = gerarChaveAESSegura();
        
        Cipher cipherAES = Cipher.getInstance(ALGORITMO_AES);
        cipherAES.init(Cipher.ENCRYPT_MODE, chaveAES);
        byte[] dadosCriptografadosAES = cipherAES.doFinal(dados);
        
        Cipher cipherRSA = Cipher.getInstance(ALGORITMO_RSA);
        cipherRSA.init(Cipher.ENCRYPT_MODE, chavePublicaDestinatario);
        byte[] chaveAESCriptografada = cipherRSA.doFinal(chaveAES.getEncoded());
        
        ByteBuffer buffer = ByteBuffer.allocate(4 + chaveAESCriptografada.length + dadosCriptografadosAES.length);
        buffer.putInt(chaveAESCriptografada.length);
        buffer.put(chaveAESCriptografada);
        buffer.put(dadosCriptografadosAES);
        
        return buffer.array();
    }
    
    public static byte[] criptografar(String mensagem, PublicKey chavePublicaDestinatario) throws Exception {
        return criptografar(mensagem.getBytes("UTF-8"), chavePublicaDestinatario);
    }
    
    public static byte[] descriptografar(byte[] dadosCriptografados, PrivateKey chavePrivada) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(dadosCriptografados);
        
        int tamanhoChaveAES = buffer.getInt();
        
        byte[] chaveAESCriptografada = new byte[tamanhoChaveAES];
        buffer.get(chaveAESCriptografada);
        
        byte[] dadosCriptografadosAES = new byte[buffer.remaining()];
        buffer.get(dadosCriptografadosAES);
        
        Cipher cipherRSA = Cipher.getInstance(ALGORITMO_RSA);
        cipherRSA.init(Cipher.DECRYPT_MODE, chavePrivada);
        byte[] chaveAESBytes = cipherRSA.doFinal(chaveAESCriptografada);
        SecretKey chaveAES = new SecretKeySpec(chaveAESBytes, "AES");
        
        Cipher cipherAES = Cipher.getInstance(ALGORITMO_AES);
        cipherAES.init(Cipher.DECRYPT_MODE, chaveAES);
        byte[] dadosDescriptografados = cipherAES.doFinal(dadosCriptografadosAES);
        
        return dadosDescriptografados;
    }
    
    public static String descriptografarParaString(byte[] dadosCriptografados, PrivateKey chavePrivada) throws Exception {
        byte[] dadosDescriptografados = descriptografar(dadosCriptografados, chavePrivada);
        return new String(dadosDescriptografados, "UTF-8");
    }
    
    public static boolean verificarChaves(PublicKey chavePublica, PrivateKey chavePrivada) {
        try {
            String mensagemTeste = "teste_validacao_chaves";
            byte[] criptografado = criptografar(mensagemTeste, chavePublica);
            String descriptografado = descriptografarParaString(criptografado, chavePrivada);
            return mensagemTeste.equals(descriptografado);
        } catch (Exception e) {
            return false;
        }
    }
}
