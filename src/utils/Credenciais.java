package utils;

import java.io.Serializable;
import java.security.PublicKey;

public class Credenciais implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String nome;
    private byte[] chavePublicaBytes;
    
    public Credenciais(String id, String nome, PublicKey chavePublica) {
        this.id = id;
        this.nome = nome;
        this.chavePublicaBytes = chavePublica.getEncoded();
    }
    
    public String getId() { return id; }
    public String getNome() { return nome; }
    public byte[] getChavePublicaBytes() { return chavePublicaBytes; }
    
    @Override
    public String toString() {
        return String.format("Credenciais[id=%s, nome=%s]", id, nome);
    }
}

