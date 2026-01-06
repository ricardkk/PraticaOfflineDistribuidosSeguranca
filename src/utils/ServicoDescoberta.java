package utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ServicoDescoberta {
    
    private static ServicoDescoberta instancia;
    private Map<String, ServicoInfo> servicosRegistrados;
    
    public enum TipoServico {
        DATACENTER,
        BORDA,
        CLIENTE
    }
    
    public static class ServicoInfo {
        private String id;
        private String nome;
        private TipoServico tipo;
        private String host;
        private int porta;
        private String descricao;
        private long timestampRegistro;
        
        public ServicoInfo(String id, String nome, TipoServico tipo, String host, int porta, String descricao) {
            this.id = id;
            this.nome = nome;
            this.tipo = tipo;
            this.host = host;
            this.porta = porta;
            this.descricao = descricao;
            this.timestampRegistro = System.currentTimeMillis();
        }
        
        public String getId() { return id; }
        public String getNome() { return nome; }
        public TipoServico getTipo() { return tipo; }
        public String getHost() { return host; }
        public int getPorta() { return porta; }
        public String getDescricao() { return descricao; }
        
        public String getEnderecoCompleto() {
            return host + ":" + porta;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s (%s) - %s:%d - %s", 
                tipo, id, nome, host, porta, descricao);
        }
    }
    
    private ServicoDescoberta() {
        this.servicosRegistrados = new ConcurrentHashMap<>();
    }
    
    public static synchronized ServicoDescoberta getInstancia() {
        if (instancia == null) {
            instancia = new ServicoDescoberta();
        }
        return instancia;
    }
    
    public void registrarServico(String id, String nome, TipoServico tipo, String host, int porta, String descricao) {
        ServicoInfo info = new ServicoInfo(id, nome, tipo, host, porta, descricao);
        servicosRegistrados.put(id, info);
        System.out.println(Cores.verde("[DISCOVERY] Servico registrado: " + info));
    }
    
    public void removerServico(String id) {
        ServicoInfo removido = servicosRegistrados.remove(id);
        if (removido != null) {
            System.out.println(Cores.amarelo("[DISCOVERY] Serviço removido: " + removido.getId()));
        }
    }
    
    public ServicoInfo descobrirServico(String id) {
        return servicosRegistrados.get(id);
    }
    
    public List<ServicoInfo> listarServicosPorTipo(TipoServico tipo) {
        List<ServicoInfo> resultado = new ArrayList<>();
        for (ServicoInfo info : servicosRegistrados.values()) {
            if (info.getTipo() == tipo) {
                resultado.add(info);
            }
        }
        return resultado;
    }
    
    public List<ServicoInfo> listarTodosServicos() {
        return new ArrayList<>(servicosRegistrados.values());
    }
    
    public ServicoInfo descobrirDatacenter() {
        List<ServicoInfo> datacenters = listarServicosPorTipo(TipoServico.DATACENTER);
        return datacenters.isEmpty() ? null : datacenters.get(0);
    }
    
    public ServicoInfo descobrirBorda() {
        List<ServicoInfo> bordas = listarServicosPorTipo(TipoServico.BORDA);
        return bordas.isEmpty() ? null : bordas.get(0);
    }
    
    public void exibirServicosDisponiveis() {
        System.out.println(Cores.ciano("\n" + "=".repeat(80)));
        System.out.println(Cores.ciano("   SERVICOS DISPONIVEIS - DISCOVERY SERVICE"));
        System.out.println(Cores.ciano("=".repeat(80)));
        
        if (servicosRegistrados.isEmpty()) {
            System.out.println("  Nenhum servico registrado.");
        } else {
            int contador = 1;
            for (ServicoInfo info : servicosRegistrados.values()) {
                String prefixo = info.getTipo() == TipoServico.DATACENTER ? "[DC]" :
                                info.getTipo() == TipoServico.BORDA ? "[BD]" : "[CL]";
                System.out.println(String.format("  %s %d. %s", prefixo, contador++, info));
            }
        }
        System.out.println();
    }
    
    public int getTotalServicos() {
        return servicosRegistrados.size();
    }
    
    public boolean existeServico(String id) {
        return servicosRegistrados.containsKey(id);
    }
}

