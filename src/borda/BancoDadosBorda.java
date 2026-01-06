package borda;

import utils.DadosAmbientais;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BancoDadosBorda {
    
    private static final String DIRETORIO = "borda_dados";
    private static final String DIRETORIO_COMPARTILHADO = 
        System.getenv().getOrDefault("SHARED_AUTH_DIR", "borda_shared");
    private static final String ARQUIVO_LEITURAS = DIRETORIO + "/leituras.csv";
    private static final String ARQUIVO_DISPOSITIVOS = DIRETORIO_COMPARTILHADO + "/dispositivos_autenticados.txt";
    private static final String ARQUIVO_ALERTAS = DIRETORIO + "/alertas_rapidos.log";
    private static final int MAX_LEITURAS = 1000;
    
    private Queue<DadosAmbientais> bufferLeituras;
    private Set<String> dispositivosAutenticados;
    
    public BancoDadosBorda() {
        this.bufferLeituras = new ConcurrentLinkedQueue<>();
        this.dispositivosAutenticados = Collections.synchronizedSet(new HashSet<>());
        inicializarDiretorios();
        carregarDispositivosAutenticados();
    }
    
    private void inicializarDiretorios() {
        File dir = new File(DIRETORIO);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dirCompartilhado = new File(DIRETORIO_COMPARTILHADO);
        if (!dirCompartilhado.exists()) {
            dirCompartilhado.mkdirs();
        }
    }
    
    public synchronized void salvarLeitura(DadosAmbientais dados) {
        bufferLeituras.add(dados);
        
        if (bufferLeituras.size() > MAX_LEITURAS) {
            bufferLeituras.poll();
        }
        
        try (FileWriter fw = new FileWriter(ARQUIVO_LEITURAS, true);
             PrintWriter pw = new PrintWriter(fw)) {
            
            if (new File(ARQUIVO_LEITURAS).length() == 0) {
                pw.println(DadosAmbientais.getCSVHeader());
            }
            pw.println(dados.toCSV());
            
        } catch (IOException e) {
            System.err.println("[BANCO-BORDA] Erro ao salvar leitura: " + e.getMessage());
        }
    }
    
    public synchronized void registrarDispositivo(String dispositivoId) {
        if (!dispositivosAutenticados.contains(dispositivoId)) {
            dispositivosAutenticados.add(dispositivoId);
            salvarDispositivosAutenticados();
        }
    }
    
    public boolean isDispositivoAutenticado(String dispositivoId) {
        if (dispositivosAutenticados.contains(dispositivoId)) {
            return true;
        }
        // Recarrega do arquivo compartilhado caso outra borda tenha autenticado
        carregarDispositivosAutenticados();
        return dispositivosAutenticados.contains(dispositivoId);
    }
    
    public synchronized void salvarAlerta(String alerta) {
        try (FileWriter fw = new FileWriter(ARQUIVO_ALERTAS, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(new Date() + " - " + alerta);
        } catch (IOException e) {
            System.err.println("[BANCO-BORDA] Erro ao salvar alerta: " + e.getMessage());
        }
    }
    
    private void salvarDispositivosAutenticados() {
        try (FileWriter fw = new FileWriter(ARQUIVO_DISPOSITIVOS);
             PrintWriter pw = new PrintWriter(fw)) {
            for (String id : dispositivosAutenticados) {
                pw.println(id);
            }
        } catch (IOException e) {
            System.err.println("[BANCO-BORDA] Erro ao salvar dispositivos: " + e.getMessage());
        }
    }
    
    private void carregarDispositivosAutenticados() {
        File arquivo = new File(ARQUIVO_DISPOSITIVOS);
        if (!arquivo.exists()) return;
        
        try (BufferedReader br = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                dispositivosAutenticados.add(linha.trim());
            }
        } catch (IOException e) {
            System.err.println("[BANCO-BORDA] Erro ao carregar dispositivos: " + e.getMessage());
        }
    }
    
    public List<DadosAmbientais> obterUltimasLeituras(int quantidade) {
        List<DadosAmbientais> resultado = new ArrayList<>();
        int count = 0;
        for (DadosAmbientais dados : bufferLeituras) {
            if (count >= quantidade) break;
            resultado.add(dados);
            count++;
        }
        return resultado;
    }
    
    public int getTotalLeituras() {
        return bufferLeituras.size();
    }
    
    public int getTotalDispositivosAutenticados() {
        return dispositivosAutenticados.size();
    }
}

