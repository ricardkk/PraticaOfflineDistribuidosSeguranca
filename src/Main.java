import borda.ServidorBorda;
import datacenter.ServidorDatacenter;
import dispositivo.DispositivoFactory;
import dispositivo.DispositivoSensor;
import cliente.ClienteFactory;
import cliente.ClienteConsulta;
import utils.Cores;
import utils.ServicoDescoberta;

import java.util.ArrayList;
import java.util.List;

public class Main {
    
    private static final int DURACAO_COLETA_SEGUNDOS = 60;
    
    public static void main(String[] args) {
        exibirBanner();
        
        ServidorDatacenter datacenter = null;
        ServidorBorda borda = null;
        List<DispositivoSensor> dispositivos = new ArrayList<>();
        
        try {
            System.out.println(Cores.amarelo("\n[FASE 1] INICIANDO INFRAESTRUTURA\n"));
            
            System.out.println("Iniciando Servidor Datacenter...");
            datacenter = new ServidorDatacenter();
            datacenter.iniciar();
            Thread.sleep(1000);
            
            System.out.println("\nIniciando Servidor Borda...");
            borda = new ServidorBorda();
            borda.iniciar();
            Thread.sleep(2000);
            
            byte[] chavePublicaBorda = borda.getChavePublicaBytes();
            byte[] chavePublicaDatacenter = datacenter.getChavePublicaBytes();
            borda.configurarChaveDatacenter(chavePublicaDatacenter);
            
            System.out.println(Cores.verde("\nInfraestrutura pronta!\n"));
            
            ServicoDescoberta.getInstancia().exibirServicosDisponiveis();
            
            System.out.println(Cores.amarelo("[FASE 2] AUTENTICANDO DISPOSITIVOS SENSORES\n"));
            
            System.out.println("Autenticando 4 dispositivos válidos...\n");
            for (int i = 0; i < 4; i++) {
                DispositivoSensor dispositivo = DispositivoFactory.criarDispositivo(i);
                
                if (dispositivo.autenticar(chavePublicaBorda)) {
                    dispositivos.add(dispositivo);
                    System.out.println(Cores.verde("  " + dispositivo.getId() + " - " + dispositivo.getNome() + " autenticado"));
                } else {
                    System.out.println(Cores.vermelho("  " + dispositivo.getId() + " falhou na autenticacao"));
                }
                Thread.sleep(500);
            }
            
            System.out.println(Cores.amarelo("\n[FASE 3] TESTANDO DISPOSITIVO COM CREDENCIAIS INVÁLIDAS\n"));
            DispositivoSensor dispositivoInvalido = DispositivoFactory.criarDispositivoInvalido();
            if (!dispositivoInvalido.autenticar(chavePublicaBorda)) {
                System.out.println(Cores.verde("  Dispositivo invalido foi corretamente rejeitado pelo sistema de seguranca\n"));
            }
            
            System.out.println(Cores.amarelo("[FASE 4] INICIANDO COLETA DE DADOS AMBIENTAIS\n"));
            System.out.println("Duração: " + DURACAO_COLETA_SEGUNDOS + " segundos (" + (DURACAO_COLETA_SEGUNDOS / 60) + " minutos)");
            System.out.println("Intervalo de envio: 2-3 segundos por dispositivo\n");
            
            for (DispositivoSensor d : dispositivos) {
                d.iniciar();
            }
            
            System.out.println(Cores.ciano("═".repeat(80)));
            System.out.println(Cores.ciano("  MONITORAMENTO EM TEMPO REAL - Observe os logs de todos os componentes"));
            System.out.println(Cores.ciano("═".repeat(80) + "\n"));
            
            int tempoRestante = DURACAO_COLETA_SEGUNDOS;
            int intervaloExibicao = 30;
            
            while (tempoRestante > 0) {
                if (tempoRestante % intervaloExibicao == 0) {
                    exibirStatusIntermediario(tempoRestante, borda, datacenter);
                }
                Thread.sleep(1000);
                tempoRestante--;
            }
            
            System.out.println(Cores.amarelo("\n[FASE 5] FINALIZANDO COLETA DE DADOS\n"));
            
            for (DispositivoSensor d : dispositivos) {
                d.parar();
            }
            
            System.out.println("Aguardando processamento dos últimos dados...");
            Thread.sleep(3000);
            
            System.out.println(Cores.verde("Coleta de dados finalizada\n"));
            
            System.out.println(Cores.amarelo("[FASE 6] MENU INTERATIVO PARA CONSULTAS DE CLIENTES\n"));
            
            System.out.println("Discovery Service disponível para localizar datacenter...\n");
            ServicoDescoberta.getInstancia().exibirServicosDisponiveis();
            
            System.out.println(Cores.verde("\nSistema pronto para consultas!"));
            System.out.println("  Você pode agora consultar relatórios pelo cliente da Reitoria.\n");
            
            ClienteFactory.iniciarMenuInterativo();
            
            System.out.println(Cores.amarelo("\n[FASE 7] ESTATISTICAS FINAIS DO SISTEMA\n"));
            borda.exibirEstatisticas();
            System.out.println();
            datacenter.exibirEstatisticas();
            
            ServicoDescoberta.getInstancia().exibirServicosDisponiveis();
            
            System.out.println(Cores.amarelo("\n[FASE 8] ENCERRANDO SISTEMA\n"));
            
            System.out.println("Parando Servidor Borda...");
            borda.parar();
            
            System.out.println("Parando Servidor Datacenter...");
            datacenter.parar();
            
            exibirRodape();
            
        } catch (Exception e) {
            System.err.println(Cores.vermelho("\nERRO durante a execucao: " + e.getMessage()));
            e.printStackTrace();
        } finally {
            if (borda != null && borda.isAtivo()) {
                borda.parar();
            }
            if (datacenter != null && datacenter.isAtivo()) {
                datacenter.parar();
            }
        }
    }
    
    private static void exibirBanner() {
        System.out.println("\n" + Cores.verde("=".repeat(80)));
        System.out.println(Cores.verde("   SISTEMA DE MONITORAMENTO AMBIENTAL UFERSA"));
        System.out.println(Cores.verde("=".repeat(80)));
        System.out.println("Arquitetura: Dispositivo -> Borda -> Datacenter <-> Cliente");
        System.out.println("Seguranca: Criptografia Hibrida (RSA 2048 + AES 256)");
        System.out.println("Monitoramento: CO2, CO, NO2, SO2, PM2.5, PM10, Temp, Umidade, Ruido, UV");
        System.out.println("Campus: Mossoro | Angicos | Pau dos Ferros | Caraubas");
        System.out.println("=".repeat(80) + "\n");
    }
    
    private static void exibirStatusIntermediario(int tempoRestante, ServidorBorda borda, ServidorDatacenter datacenter) {
        int minutos = tempoRestante / 60;
        int segundos = tempoRestante % 60;
        
        System.out.println("\n" + Cores.amarelo("─".repeat(80)));
        System.out.println(Cores.amarelo(String.format("  TEMPO RESTANTE: %d minuto(s) e %d segundo(s)", minutos, segundos)));
        System.out.println(Cores.amarelo("─".repeat(80)));
        
        borda.exibirEstatisticas();
        System.out.println();
        datacenter.exibirEstatisticas();
        
        System.out.println(Cores.amarelo("─".repeat(80) + "\n"));
    }
    
    private static void exibirRodape() {
        System.out.println("\n" + Cores.verde("=".repeat(80)));
        System.out.println(Cores.verde("   SIMULACAO CONCLUIDA COM SUCESSO!"));
        System.out.println(Cores.verde("=".repeat(80)) + "\n");
    }
}
