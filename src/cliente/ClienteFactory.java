package cliente;

import utils.Cores;

public class ClienteFactory {
    
    private static final String HOST_DATACENTER = 
        System.getenv().getOrDefault("DATACENTER_HOST", "localhost");
    private static final int PORTA_HTTP = Integer.parseInt(
        System.getenv().getOrDefault("DATACENTER_HTTP_PORT", "8080"));
    
    public static ClienteConsulta criarClienteReitoria() throws Exception {
        return new ClienteConsulta("CLI-REITORIA", "Reitoria UFERSA", HOST_DATACENTER, PORTA_HTTP);
    }
    
    public static void iniciarMenuInterativo() throws Exception {
        System.out.println("\n" + Cores.ciano("=".repeat(80)));
        System.out.println(Cores.verde("   SISTEMA DE CONSULTAS - REITORIA UFERSA"));
        System.out.println(Cores.ciano("=".repeat(80)));
        
        ClienteConsulta cliente = criarClienteReitoria();
        MenuCliente menu = new MenuCliente(cliente);
        menu.iniciar();
    }
}

