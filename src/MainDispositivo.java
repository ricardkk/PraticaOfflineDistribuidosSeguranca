import dispositivo.DispositivoFactory;
import dispositivo.DispositivoSensor;
import dispositivo.GeradorDadosAmbientais;
import utils.Cores;
import utils.DadosAmbientais;
import utils.ServicoSeguranca;

import java.security.KeyPair;

public class MainDispositivo {
    
    private static final boolean MODO_ATAQUE = 
        "true".equalsIgnoreCase(System.getenv().getOrDefault("MODO_ATAQUE", "false"));
    
    public static void main(String[] args) {
        exibirBanner();
        
        if (MODO_ATAQUE) {
            System.out.println(Cores.vermelho("!!! MODO ATAQUE ATIVADO !!!"));
            System.out.println(Cores.vermelho("Este sensor enviará dados maliciosos para testar o IDS.\n"));
        }
        
        try {
            DispositivoSensor dispositivo = criarDispositivo();
            
            if (dispositivo == null) {
                System.err.println(Cores.vermelho("Falha ao criar dispositivo. Encerrando."));
                System.exit(1);
            }
            
            System.out.println(Cores.ciano("Dispositivo criado: " + dispositivo.getId()));
            System.out.println("  Nome: " + dispositivo.getNome());
            
            String hostBorda = DispositivoFactory.getHostBorda();
            int portaHttp = DispositivoFactory.getPortaHttpBorda();
            
            System.out.println(Cores.verde("[DISCOVERY] Consultando serviço de nomes..."));
            System.out.println(Cores.verde("[DISCOVERY] Borda localizada em: " + hostBorda + ":" + portaHttp));
            
            System.out.println(Cores.amarelo("\nIniciando autenticação via rede..."));
            System.out.println("  Borda HTTP: " + hostBorda + ":" + portaHttp);
            
            if (!dispositivo.autenticarViaRede(hostBorda, portaHttp)) {
                System.err.println(Cores.vermelho("Falha na autenticação. Encerrando."));
                System.exit(1);
            }
            
            System.out.println(Cores.verde("\nAutenticação bem-sucedida!"));
            
            // Shutdown hook para parar graciosamente
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(Cores.amarelo("\nRecebido sinal de encerramento..."));
                dispositivo.parar();
            }));
            
            if (MODO_ATAQUE) {
                System.out.println(Cores.vermelho("\nIniciando envio de dados MALICIOSOS...\n"));
                executarAtaque(dispositivo);
            } else {
                System.out.println(Cores.ciano("Iniciando coleta de dados...\n"));
                dispositivo.iniciar();
            }
            
            // Manter o processo rodando
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            System.err.println(Cores.vermelho("ERRO: " + e.getMessage()));
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void executarAtaque(DispositivoSensor dispositivo) {
        new Thread(() -> {
            try {
                int contador = 0;
                while (true) {
                    contador++;
                    
                    // Gerar dados absurdos
                    DadosAmbientais dados = new DadosAmbientais();
                    dados.setDispositivoId(dispositivo.getId());
                    dados.setLocalizacao("ATAQUE");
                    
                    // Valores absurdos para acionar o IDS
                    dados.setTemperatura(200.0 + Math.random() * 100);  // 200-300°C (absurdo!)
                    dados.setCo2(50000.0 + Math.random() * 10000);      // 50000+ ppm (absurdo!)
                    dados.setUv(50.0 + Math.random() * 20);             // UV > 50 (absurdo!)
                    dados.setUmidade(150.0);                            // > 100% (impossível!)
                    dados.setCo(100.0);
                    dados.setNo2(500.0);
                    dados.setSo2(200.0);
                    dados.setPm25(500.0);
                    dados.setPm10(1000.0);
                    dados.setRuido(120.0);
                    
                    System.out.println(Cores.vermelho("[ATAQUE #" + contador + "] Enviando dados maliciosos:"));
                    System.out.println(Cores.vermelho("  Temp: " + dados.getTemperatura() + "°C"));
                    System.out.println(Cores.vermelho("  CO2: " + dados.getCo2() + " ppm"));
                    System.out.println(Cores.vermelho("  UV: " + dados.getUv()));
                    
                    dispositivo.enviarDadosMaliciosos(dados);
                    
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                System.err.println(Cores.vermelho("Erro no ataque: " + e.getMessage()));
            }
        }, "Ataque-Thread").start();
    }
    
    private static DispositivoSensor criarDispositivo() throws Exception {
        // Verificar se foi especificado um índice de dispositivo predefinido
        String indexStr = System.getenv("SENSOR_INDEX");
        if (indexStr != null && !indexStr.isEmpty()) {
            int index = Integer.parseInt(indexStr);
            System.out.println("Usando dispositivo predefinido índice: " + index);
            return DispositivoFactory.criarDispositivo(index);
        }
        
        // Caso contrário, usar configuração customizada
        String id = System.getenv().getOrDefault("SENSOR_ID", "SENSOR-DOCKER");
        String nome = System.getenv().getOrDefault("SENSOR_NOME", "Sensor Docker");
        String localizacao = System.getenv().getOrDefault("SENSOR_LOCALIZACAO", "Container Docker");
        
        System.out.println("Criando dispositivo customizado:");
        System.out.println("  ID: " + id);
        System.out.println("  Nome: " + nome);
        System.out.println("  Localização: " + localizacao);
        
        return DispositivoFactory.criarDispositivoCustomizado(id, nome, localizacao);
    }
    
    private static void exibirBanner() {
        System.out.println("\n" + Cores.verde("=".repeat(60)));
        System.out.println(Cores.verde("   DISPOSITIVO SENSOR - SISTEMA DE MONITORAMENTO UFERSA"));
        System.out.println(Cores.verde("=".repeat(60)));
        System.out.println("Modo: Container Docker");
        System.out.println("=".repeat(60) + "\n");
    }
}

