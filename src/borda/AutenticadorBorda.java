package borda;

import utils.Cores;
import utils.Credenciais;
import utils.Mensagem;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ConcurrentHashMap;

public class AutenticadorBorda {
    
    private static final String[] IDS_AUTORIZADOS = {
        "SENSOR-MOSSORO",
        "SENSOR-ANGICOS", 
        "SENSOR-PAUDOSFERROS",
        "SENSOR-CARAUBAS",
        "SENSOR-HACKER"
    };
    
    private ConcurrentHashMap<String, PublicKey> chavesPublicasDispositivos;
    private BancoDadosBorda bancoDados;
    
    public AutenticadorBorda(BancoDadosBorda bancoDados) {
        this.chavesPublicasDispositivos = new ConcurrentHashMap<>();
        this.bancoDados = bancoDados;
    }
    
    private boolean isDispositivoAutorizado(String dispositivoId) {
        for (String id : IDS_AUTORIZADOS) {
            if (id.equals(dispositivoId)) {
                return true;
            }
        }
        return false;
    }
    
    public synchronized Mensagem autenticar(Mensagem mensagemAutenticacao) {
        try {
            if (mensagemAutenticacao.getTipo() != Mensagem.Tipo.AUTENTICACAO) {
                return new Mensagem(Mensagem.Tipo.ERRO, "BORDA", 
                    "Tipo de mensagem incorreto");
            }
            
            Credenciais credenciais = (Credenciais) mensagemAutenticacao.getConteudo();
            String dispositivoId = credenciais.getId();
            
            if (!isDispositivoAutorizado(dispositivoId)) {
                log(Cores.vermelho("NEGADO: Dispositivo " + dispositivoId + " nao esta na lista de autorizados"));
                return new Mensagem(Mensagem.Tipo.ERRO, "BORDA", 
                    "Dispositivo nao autorizado no sistema");
            }
            
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey chavePublica = keyFactory.generatePublic(
                new X509EncodedKeySpec(credenciais.getChavePublicaBytes())
            );
            
            if (bancoDados.isDispositivoAutenticado(dispositivoId)) {
                chavesPublicasDispositivos.put(dispositivoId, chavePublica);
                log(Cores.amarelo("Dispositivo " + dispositivoId + " ja autenticado anteriormente (recarregado em memoria)"));
                return new Mensagem(Mensagem.Tipo.ACK, "BORDA", 
                    "Ja autenticado");
            }
            
            chavesPublicasDispositivos.put(dispositivoId, chavePublica);
            bancoDados.registrarDispositivo(dispositivoId);
            
            log(Cores.verde("Dispositivo autenticado: " + dispositivoId + " - " + credenciais.getNome()));
            
            return new Mensagem(Mensagem.Tipo.ACK, "BORDA", 
                "Autenticacao bem-sucedida");
                
        } catch (Exception e) {
            log(Cores.vermelho("ERRO na autenticacao: " + e.getMessage()));
            return new Mensagem(Mensagem.Tipo.ERRO, "BORDA", 
                "Falha na autenticacao: " + e.getMessage());
        }
    }
    
    public PublicKey obterChavePublicaDispositivo(String dispositivoId) {
        return chavesPublicasDispositivos.get(dispositivoId);
    }
    
    public boolean isDispositivoAutenticado(String dispositivoId) {
        // Verifica em memória primeiro
        if (chavesPublicasDispositivos.containsKey(dispositivoId)) {
            return true;
        }
        // Se não está em memória, verifica no arquivo compartilhado
        return bancoDados.isDispositivoAutenticado(dispositivoId);
    }
    
    public int getTotalDispositivosAtivos() {
        return chavesPublicasDispositivos.size();
    }
    
    private void log(String mensagem) {
        System.out.println("[AUTENTICADOR-BORDA] " + mensagem);
    }
}

