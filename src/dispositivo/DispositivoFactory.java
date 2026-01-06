package dispositivo;

import utils.ServicoSeguranca;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

public class DispositivoFactory {
    
    private static final String HOST_BORDA = 
        System.getenv().getOrDefault("BORDA_HOST", "localhost");
    private static final int PORTA_BORDA = Integer.parseInt(
        System.getenv().getOrDefault("BORDA_UDP_PORT", "5000"));
    private static final int PORTA_HTTP_BORDA = Integer.parseInt(
        System.getenv().getOrDefault("BORDA_HTTP_PORT", "5001"));
    
    private static final String[][] CONFIGURACOES_DISPOSITIVOS = {
        {"SENSOR-MOSSORO", "Campus Mossoro", "UFERSA - Mossoro, RN"},
        {"SENSOR-ANGICOS", "Campus Angicos", "UFERSA - Angicos, RN"},
        {"SENSOR-PAUDOSFERROS", "Campus Pau dos Ferros", "UFERSA - Pau dos Ferros, RN"},
        {"SENSOR-CARAUBAS", "Campus Caraubas", "UFERSA - Caraubas, RN"}
    };
    
    public static DispositivoSensor criarDispositivo(int indice) throws Exception {
        if (indice < 0 || indice >= CONFIGURACOES_DISPOSITIVOS.length) {
            throw new IllegalArgumentException("Índice inválido: " + indice);
        }
        
        String[] config = CONFIGURACOES_DISPOSITIVOS[indice];
        KeyPair parChaves = ServicoSeguranca.gerarParChavesRSA();
        
        return new DispositivoSensor(
            config[0],
            config[1],
            config[2],
            parChaves,
            HOST_BORDA,
            PORTA_BORDA
        );
    }
    
    public static DispositivoSensor criarDispositivoInvalido() throws Exception {
        KeyPair parChavesInvalidas = ServicoSeguranca.gerarParChavesRSA();
        
        return new DispositivoSensor(
            "SENSOR-XX",
            "Dispositivo Invasor",
            "Localização Desconhecida",
            parChavesInvalidas,
            HOST_BORDA,
            PORTA_BORDA
        );
    }
    
    public static DispositivoSensor criarDispositivoCustomizado(
            String id, String nome, String localizacao) throws Exception {
        
        KeyPair parChaves = ServicoSeguranca.gerarParChavesRSA();
        
        return new DispositivoSensor(
            id,
            nome,
            localizacao,
            parChaves,
            HOST_BORDA,
            PORTA_BORDA
        );
    }
    
    public static List<DispositivoSensor> criarTodosDispositivos() throws Exception {
        List<DispositivoSensor> dispositivos = new ArrayList<>();
        for (int i = 0; i < CONFIGURACOES_DISPOSITIVOS.length; i++) {
            dispositivos.add(criarDispositivo(i));
        }
        return dispositivos;
    }
    
    public static int getQuantidadeDispositivos() {
        return CONFIGURACOES_DISPOSITIVOS.length;
    }
    
    public static String getHostBorda() {
        return HOST_BORDA;
    }
    
    public static int getPortaHttpBorda() {
        return PORTA_HTTP_BORDA;
    }
}

