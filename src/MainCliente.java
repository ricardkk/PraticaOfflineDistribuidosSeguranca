import cliente.ClienteFactory;
import cliente.ClienteConsulta;
import cliente.MenuCliente;
import utils.Cores;

public class MainCliente {
    
    public static void main(String[] args) {
        exibirBanner();
        
        try {
            // Aguardar datacenter estar disponível
            aguardarDatacenter();
            
            String modoInterativo = System.getenv().getOrDefault("MODO_INTERATIVO", "true");
            
            if ("true".equalsIgnoreCase(modoInterativo)) {
                System.out.println(Cores.ciano("Modo: Interativo\n"));
                ClienteFactory.iniciarMenuInterativo();
            } else {
                System.out.println(Cores.ciano("Modo: Consultas Automáticas\n"));
                executarConsultasAutomaticas();
            }
            
            System.out.println(Cores.verde("\nCliente encerrado."));
            
        } catch (Exception e) {
            System.err.println(Cores.vermelho("ERRO: " + e.getMessage()));
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void aguardarDatacenter() {
        String host = System.getenv().getOrDefault("DATACENTER_HOST", "localhost");
        String porta = System.getenv().getOrDefault("DATACENTER_HTTP_PORT", "8080");
        
        System.out.println(Cores.verde("[DISCOVERY] Consultando serviço de nomes..."));
        System.out.println(Cores.verde("[DISCOVERY] Datacenter localizado em: " + host + ":" + porta));
        System.out.println();
        
        System.out.println("Datacenter configurado: " + host + ":" + porta);
        System.out.println(Cores.amarelo("Aguardando datacenter estar disponível..."));
        
        int maxTentativas = 20;
        int intervaloMs = 3000;
        
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                java.net.URL url = new java.net.URL("http://" + host + ":" + porta + "/publickey");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    System.out.println(Cores.verde("Datacenter disponível!\n"));
                    return;
                }
            } catch (Exception e) {
                System.out.println("  Tentativa " + tentativa + "/" + maxTentativas + " - aguardando...");
            }
            
            try {
                Thread.sleep(intervaloMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println(Cores.amarelo("Datacenter pode não estar totalmente pronto, tentando conectar mesmo assim..."));
    }
    
    private static void executarConsultasAutomaticas() throws Exception {
        ClienteConsulta cliente = ClienteFactory.criarClienteReitoria();
        
        // Em Docker, conectar diretamente sem usar Discovery Service
        if (!cliente.conectar()) {
            System.err.println(Cores.vermelho("Falha ao conectar ao datacenter"));
            return;
        }
        
        System.out.println(Cores.ciano("\n" + "=".repeat(60)));
        System.out.println(Cores.ciano("EXECUTANDO CONSULTAS AUTOMÁTICAS"));
        System.out.println(Cores.ciano("=".repeat(60) + "\n"));
        
        // Executar todas as consultas disponíveis
        cliente.analisarQualidadeAr();
        Thread.sleep(1000);
        
        cliente.analisarMapaPoluicao();
        Thread.sleep(1000);
        
        cliente.analisarIlhasCalor();
        Thread.sleep(1000);
        
        cliente.analisarRiscoSaude();
        Thread.sleep(1000);
        
        cliente.analisarConfortoAmbiental();
        
        System.out.println(Cores.verde("\n" + "=".repeat(60)));
        System.out.println(Cores.verde("CONSULTAS CONCLUÍDAS"));
        System.out.println(Cores.verde("=".repeat(60)));
    }
    
    private static void exibirBanner() {
        System.out.println("\n" + Cores.ciano("=".repeat(60)));
        System.out.println(Cores.ciano("   CLIENTE REITORIA - SISTEMA DE MONITORAMENTO UFERSA"));
        System.out.println(Cores.ciano("=".repeat(60)));
        System.out.println("Modo: Container Docker");
        System.out.println("=".repeat(60) + "\n");
    }
}

