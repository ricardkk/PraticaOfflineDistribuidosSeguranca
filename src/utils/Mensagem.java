package utils;

import java.io.*;
import java.util.zip.CRC32;

public class Mensagem implements Serializable {
    private static final long serialVersionUID = 2L;
    
    public enum Tipo {
        AUTENTICACAO,
        DADOS_SENSOR,
        ACK,
        ERRO,
        RELATORIO,
        COMANDO_IDS,
        LOG_SEGURANCA
    }
    
    private Tipo tipo;
    private String remetente;
    private Object conteudo;
    private long timestamp;
    private long checksum;
    
    public Mensagem(Tipo tipo, String remetente, Object conteudo) {
        this.tipo = tipo;
        this.remetente = remetente;
        this.conteudo = conteudo;
        this.timestamp = System.currentTimeMillis();
        this.checksum = 0;
    }
    
    public Tipo getTipo() { return tipo; }
    public String getRemetente() { return remetente; }
    public Object getConteudo() { return conteudo; }
    public long getTimestamp() { return timestamp; }
    public long getChecksum() { return checksum; }
    
    public byte[] serializar() throws IOException {
        ByteArrayOutputStream baosTemp = new ByteArrayOutputStream();
        ObjectOutputStream oosTemp = new ObjectOutputStream(baosTemp);
        
        long checksumOriginal = this.checksum;
        this.checksum = 0;
        
        oosTemp.writeObject(this);
        oosTemp.close();
        byte[] dadosSemChecksum = baosTemp.toByteArray();
        
        CRC32 crc = new CRC32();
        crc.update(dadosSemChecksum);
        this.checksum = crc.getValue();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        oos.close();
        
        return baos.toByteArray();
    }
    
    public static Mensagem desserializar(byte[] dados) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(dados);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Mensagem mensagem = (Mensagem) ois.readObject();
        ois.close();
        
        long checksumRecebido = mensagem.checksum;
        mensagem.checksum = 0;
        
        ByteArrayOutputStream baosVerificacao = new ByteArrayOutputStream();
        ObjectOutputStream oosVerificacao = new ObjectOutputStream(baosVerificacao);
        oosVerificacao.writeObject(mensagem);
        oosVerificacao.close();
        
        CRC32 crc = new CRC32();
        crc.update(baosVerificacao.toByteArray());
        long checksumCalculado = crc.getValue();
        
        mensagem.checksum = checksumRecebido;
        
        if (checksumRecebido != checksumCalculado) {
            throw new IOException("Integridade corrompida: checksum esperado=" + 
                checksumRecebido + ", calculado=" + checksumCalculado);
        }
        
        return mensagem;
    }
    
    public boolean verificarIntegridade() {
        try {
            long checksumOriginal = this.checksum;
            this.checksum = 0;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            oos.close();
            
            CRC32 crc = new CRC32();
            crc.update(baos.toByteArray());
            long checksumCalculado = crc.getValue();
            
            this.checksum = checksumOriginal;
            
            return checksumOriginal == checksumCalculado;
        } catch (IOException e) {
            return false;
        }
    }
}

