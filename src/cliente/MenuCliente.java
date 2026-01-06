package cliente;

import utils.Cores;
import java.util.Scanner;

public class MenuCliente {
    
    private ClienteConsulta cliente;
    private Scanner scanner;
    private boolean executando;
    
    public MenuCliente(ClienteConsulta cliente) {
        this.cliente = cliente;
        this.scanner = new Scanner(System.in);
        this.executando = true;
    }
    
    public void iniciar() {
        if (!cliente.descobrirEConectar()) {
            System.out.println(Cores.vermelho("\nNao foi possivel conectar ao datacenter."));
            return;
        }
        
        exibirBanner();
        
        while (executando) {
            exibirMenu();
            int opcao = lerOpcao();
            processarOpcao(opcao);
        }
        
        scanner.close();
    }
    
    private void exibirBanner() {
        System.out.println("\n" + Cores.ciano("=".repeat(80)));
        System.out.println(Cores.verde("   SISTEMA DE CONSULTA DE RELATORIOS AMBIENTAIS UFERSA"));
        System.out.println("Cliente: " + cliente.getNome());
        System.out.println(Cores.ciano("=".repeat(80)));
    }
    
    private void exibirMenu() {
        System.out.println("\n" + Cores.amarelo("=".repeat(80)));
        System.out.println(Cores.amarelo("   MENU DE RELATORIOS"));
        System.out.println(Cores.amarelo("=".repeat(80)));
        System.out.println("  1. Indice de Qualidade do Ar (IQA)");
        System.out.println("  2. Mapa de Calor de Poluicao");
        System.out.println("  3. Alerta de Ilhas de Calor Urbanas");
        System.out.println("  4. Previsao de Risco a Saude");
        System.out.println("  5. Analise de Conforto Ambiental");
        System.out.println("  6. Ver Todos os Relatorios");
        System.out.println("  7. Status do Sistema");
        System.out.println("  0. Sair");
        System.out.println("=".repeat(80));
        System.out.print(Cores.ciano("\nEscolha uma opcao: "));
    }
    
    private int lerOpcao() {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    private void processarOpcao(int opcao) {
        System.out.println();
        
        switch (opcao) {
            case 1:
                cliente.analisarQualidadeAr();
                pausar();
                break;
                
            case 2:
                cliente.analisarMapaPoluicao();
                pausar();
                break;
                
            case 3:
                cliente.analisarIlhasCalor();
                pausar();
                break;
                
            case 4:
                cliente.analisarRiscoSaude();
                pausar();
                break;
                
            case 5:
                cliente.analisarConfortoAmbiental();
                pausar();
                break;
                
            case 6:
                verTodosRelatorios();
                pausar();
                break;
                
            case 7:
                verStatus();
                pausar();
                break;
                
            case 0:
                System.out.println(Cores.verde("Encerrando cliente..."));
                executando = false;
                break;
                
            default:
                System.out.println(Cores.vermelho("Opcao invalida! Tente novamente."));
        }
    }
    
    private void verTodosRelatorios() {
        System.out.println(Cores.ciano("=".repeat(80)));
        System.out.println(Cores.ciano("   CONSULTANDO TODOS OS RELATORIOS"));
        System.out.println(Cores.ciano("=".repeat(80)) + "\n");
        
        cliente.analisarQualidadeAr();
        System.out.println("\n" + "─".repeat(80) + "\n");
        
        cliente.analisarMapaPoluicao();
        System.out.println("\n" + "─".repeat(80) + "\n");
        
        cliente.analisarIlhasCalor();
        System.out.println("\n" + "─".repeat(80) + "\n");
        
        cliente.analisarRiscoSaude();
        System.out.println("\n" + "─".repeat(80) + "\n");
        
        cliente.analisarConfortoAmbiental();
    }
    
    private void verStatus() {
        String status = cliente.consultarStatus();
        if (status != null) {
            System.out.println(Cores.ciano("=".repeat(80)));
            System.out.println(Cores.ciano("   STATUS DO SISTEMA"));
            System.out.println(Cores.ciano("=".repeat(80)));
            System.out.println(status);
        }
    }
    
    private void pausar() {
        System.out.print(Cores.amarelo("\nPressione ENTER para continuar..."));
        scanner.nextLine();
    }
}

