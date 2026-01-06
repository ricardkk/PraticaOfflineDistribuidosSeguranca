package datacenter;

import utils.DadosAmbientais;
import java.util.*;
import java.util.stream.Collectors;

public class ProcessadorRelatorios {
    
    private BancoDadosDatacenter bancoDados;
    
    public ProcessadorRelatorios(BancoDadosDatacenter bancoDados) {
        this.bancoDados = bancoDados;
    }
    
    public Relatorio gerarRelatorioQualidadeAr() {
        List<DadosAmbientais> leituras = bancoDados.obterTodasLeituras();
        
        if (leituras.isEmpty()) {
            return new Relatorio(
                Relatorio.TipoRelatorio.QUALIDADE_AR,
                "Índice de Qualidade do Ar (IQA)",
                "Dados insuficientes para gerar relatório."
            );
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("INDICE DE QUALIDADE DO AR POR LOCALIDADE\n\n");
        
        Map<String, List<DadosAmbientais>> porLocal = bancoDados.obterLeiturasPorLocalizacao();
        
        List<Map.Entry<String, Double>> rankings = new ArrayList<>();
        
        for (Map.Entry<String, List<DadosAmbientais>> entry : porLocal.entrySet()) {
            String local = entry.getKey();
            List<DadosAmbientais> dados = entry.getValue();
            
            double pm25Medio = dados.stream().mapToDouble(DadosAmbientais::getPm25).average().orElse(0);
            double pm10Medio = dados.stream().mapToDouble(DadosAmbientais::getPm10).average().orElse(0);
            double co2Medio = dados.stream().mapToDouble(DadosAmbientais::getCo2).average().orElse(0);
            
            double iqa = calcularIQA(pm25Medio, pm10Medio, co2Medio);
            rankings.add(new AbstractMap.SimpleEntry<>(local, iqa));
            
            String classificacao = classificarQualidadeAr(iqa);
            sb.append(String.format("LOCAL: %s\n", local));
            sb.append(String.format("   IQA: %.1f - %s\n", iqa, classificacao));
            sb.append(String.format("   PM2.5: %.1f ug/m3 | PM10: %.1f ug/m3 | CO2: %.1f ppm\n", 
                pm25Medio, pm10Medio, co2Medio));
            sb.append(String.format("   Amostras analisadas: %d\n\n", dados.size()));
        }
        
        rankings.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        sb.append("\nRANKING DE QUALIDADE (melhor para pior):\n");
        for (int i = 0; i < rankings.size(); i++) {
            sb.append(String.format("   %d. %s (IQA: %.1f)\n", 
                i + 1, rankings.get(i).getKey(), rankings.get(i).getValue()));
        }
        
        return new Relatorio(
            Relatorio.TipoRelatorio.QUALIDADE_AR,
            "Índice de Qualidade do Ar (IQA)",
            sb.toString()
        );
    }
    
    public Relatorio gerarMapaPoluicao() {
        List<DadosAmbientais> leituras = bancoDados.obterTodasLeituras();
        
        if (leituras.isEmpty()) {
            return new Relatorio(
                Relatorio.TipoRelatorio.MAPA_POLUICAO,
                "Mapa de Calor de Poluição",
                "Dados insuficientes para gerar relatório."
            );
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("MAPA DE CALOR - CONCENTRACAO DE POLUENTES\n\n");
        
        Map<String, List<DadosAmbientais>> porLocal = bancoDados.obterLeiturasPorLocalizacao();
        
        sb.append("DIOXIDO DE CARBONO (CO2):\n");
        porLocal.entrySet().stream()
            .sorted((a, b) -> Double.compare(
                b.getValue().stream().mapToDouble(DadosAmbientais::getCo2).average().orElse(0),
                a.getValue().stream().mapToDouble(DadosAmbientais::getCo2).average().orElse(0)
            ))
            .forEach(entry -> {
                double media = entry.getValue().stream().mapToDouble(DadosAmbientais::getCo2).average().orElse(0);
                sb.append(String.format("   %s: %.1f ppm\n", 
                    entry.getKey(), media));
            });
        
        sb.append("\nMATERIAL PARTICULADO (PM2.5):\n");
        porLocal.entrySet().stream()
            .sorted((a, b) -> Double.compare(
                b.getValue().stream().mapToDouble(DadosAmbientais::getPm25).average().orElse(0),
                a.getValue().stream().mapToDouble(DadosAmbientais::getPm25).average().orElse(0)
            ))
            .forEach(entry -> {
                double media = entry.getValue().stream().mapToDouble(DadosAmbientais::getPm25).average().orElse(0);
                sb.append(String.format("   %s: %.1f ug/m3\n", 
                    entry.getKey(), media));
            });
        
        sb.append("\nDIOXIDO DE NITROGENIO (NO2):\n");
        porLocal.entrySet().stream()
            .sorted((a, b) -> Double.compare(
                b.getValue().stream().mapToDouble(DadosAmbientais::getNo2).average().orElse(0),
                a.getValue().stream().mapToDouble(DadosAmbientais::getNo2).average().orElse(0)
            ))
            .forEach(entry -> {
                double media = entry.getValue().stream().mapToDouble(DadosAmbientais::getNo2).average().orElse(0);
                sb.append(String.format("   %s: %.1f ppb\n", 
                    entry.getKey(), media));
            });
        
        return new Relatorio(
            Relatorio.TipoRelatorio.MAPA_POLUICAO,
            "Mapa de Calor de Poluição",
            sb.toString()
        );
    }
    
    public Relatorio gerarAlertaIlhasCalor() {
        List<DadosAmbientais> leituras = bancoDados.obterTodasLeituras();
        
        if (leituras.isEmpty()) {
            return new Relatorio(
                Relatorio.TipoRelatorio.ILHAS_CALOR,
                "Alerta de Ilhas de Calor Urbanas",
                "Dados insuficientes para gerar relatório."
            );
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("ANALISE DE ILHAS DE CALOR URBANAS\n\n");
        
        Map<String, List<DadosAmbientais>> porLocal = bancoDados.obterLeiturasPorLocalizacao();
        
        double tempGlobalMedia = leituras.stream()
            .mapToDouble(DadosAmbientais::getTemperatura)
            .average()
            .orElse(25);
        
        sb.append(String.format("Temperatura média global: %.1f°C\n\n", tempGlobalMedia));
        
        List<Map.Entry<String, Double>> anomalias = new ArrayList<>();
        
        for (Map.Entry<String, List<DadosAmbientais>> entry : porLocal.entrySet()) {
            double tempLocal = entry.getValue().stream()
                .mapToDouble(DadosAmbientais::getTemperatura)
                .average()
                .orElse(0);
            
            double diferenca = tempLocal - tempGlobalMedia;
            anomalias.add(new AbstractMap.SimpleEntry<>(entry.getKey(), diferenca));
        }
        
        anomalias.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        sb.append("ANOMALIAS TERMICAS:\n");
        for (Map.Entry<String, Double> anomalia : anomalias) {
            String nivel = anomalia.getValue() > 2 ? "[QUENTE]" : 
                          anomalia.getValue() > 0 ? "[ELEVADO]" : 
                          anomalia.getValue() < -2 ? "[FRIO]" : "[NORMAL]";
            
            sb.append(String.format("   %s %s: %+.1fC (%.1fC)\n", 
                nivel, anomalia.getKey(), anomalia.getValue(), 
                tempGlobalMedia + anomalia.getValue()));
            
            if (anomalia.getValue() > 3) {
                sb.append("      ALERTA: ILHA DE CALOR SEVERA DETECTADA!\n");
            } else if (anomalia.getValue() > 2) {
                sb.append("      ATENCAO: Ilha de calor moderada\n");
            }
        }
        
        return new Relatorio(
            Relatorio.TipoRelatorio.ILHAS_CALOR,
            "Alerta de Ilhas de Calor Urbanas",
            sb.toString()
        );
    }
    
    public Relatorio gerarPrevisaoRiscoSaude() {
        List<DadosAmbientais> leituras = bancoDados.obterTodasLeituras();
        
        if (leituras.isEmpty()) {
            return new Relatorio(
                Relatorio.TipoRelatorio.RISCO_SAUDE,
                "Previsão de Risco à Saúde",
                "Dados insuficientes para gerar relatório."
            );
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("AVALIACAO DE RISCO A SAUDE PUBLICA\n\n");
        
        Map<String, List<DadosAmbientais>> porLocal = bancoDados.obterLeiturasPorLocalizacao();
        
        for (Map.Entry<String, List<DadosAmbientais>> entry : porLocal.entrySet()) {
            String local = entry.getKey();
            List<DadosAmbientais> dados = entry.getValue();
            
            double pm25 = dados.stream().mapToDouble(DadosAmbientais::getPm25).average().orElse(0);
            double uv = dados.stream().mapToDouble(DadosAmbientais::getUv).average().orElse(0);
            double no2 = dados.stream().mapToDouble(DadosAmbientais::getNo2).average().orElse(0);
            double co = dados.stream().mapToDouble(DadosAmbientais::getCo).average().orElse(0);
            
            int pontuacaoRisco = 0;
            List<String> alertas = new ArrayList<>();
            
            if (pm25 > 100) {
                pontuacaoRisco += 3;
                alertas.add("PM2.5 crítico - risco respiratório alto");
            } else if (pm25 > 75) {
                pontuacaoRisco += 2;
                alertas.add("PM2.5 elevado - grupos sensíveis em risco");
            }
            
            if (uv > 8) {
                pontuacaoRisco += 2;
                alertas.add("UV muito alto - risco de câncer de pele");
            } else if (uv > 6) {
                pontuacaoRisco += 1;
                alertas.add("UV alto - recomenda-se proteção solar");
            }
            
            if (no2 > 150) {
                pontuacaoRisco += 2;
                alertas.add("NO2 elevado - irritação das vias respiratórias");
            }
            
            if (co > 35) {
                pontuacaoRisco += 2;
                alertas.add("CO elevado - risco de intoxicação");
            }
            
            String nivelRisco = pontuacaoRisco >= 6 ? "CRITICO" :
                               pontuacaoRisco >= 4 ? "ALTO" :
                               pontuacaoRisco >= 2 ? "MODERADO" : "BAIXO";
            
            sb.append(String.format("LOCAL: %s\n", local));
            sb.append(String.format("   Nível de Risco: %s (pontos: %d)\n", nivelRisco, pontuacaoRisco));
            
            if (!alertas.isEmpty()) {
                sb.append("   Alertas:\n");
                for (String alerta : alertas) {
                    sb.append(String.format("   • %s\n", alerta));
                }
            }
            
            sb.append("\n");
        }
        
        return new Relatorio(
            Relatorio.TipoRelatorio.RISCO_SAUDE,
            "Previsão de Risco à Saúde",
            sb.toString()
        );
    }
    
    public Relatorio gerarAnaliseConfortoAmbiental() {
        List<DadosAmbientais> leituras = bancoDados.obterTodasLeituras();
        
        if (leituras.isEmpty()) {
            return new Relatorio(
                Relatorio.TipoRelatorio.CONFORTO_AMBIENTAL,
                "Análise de Conforto Ambiental",
                "Dados insuficientes para gerar relatório."
            );
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("INDICE DE CONFORTO AMBIENTAL URBANO\n\n");
        
        Map<String, List<DadosAmbientais>> porLocal = bancoDados.obterLeiturasPorLocalizacao();
        
        List<Map.Entry<String, Double>> rankingConforto = new ArrayList<>();
        
        for (Map.Entry<String, List<DadosAmbientais>> entry : porLocal.entrySet()) {
            String local = entry.getKey();
            List<DadosAmbientais> dados = entry.getValue();
            
            double temp = dados.stream().mapToDouble(DadosAmbientais::getTemperatura).average().orElse(0);
            double umidade = dados.stream().mapToDouble(DadosAmbientais::getUmidade).average().orElse(0);
            double ruido = dados.stream().mapToDouble(DadosAmbientais::getRuido).average().orElse(0);
            
            double indiceConforto = calcularIndiceConforto(temp, umidade, ruido);
            rankingConforto.add(new AbstractMap.SimpleEntry<>(local, indiceConforto));
            
            String classificacao = classificarConforto(indiceConforto);
            
            sb.append(String.format("LOCAL: %s\n", local));
            sb.append(String.format("   Indice de Conforto: %.1f/100 - %s\n", indiceConforto, classificacao));
            sb.append(String.format("   - Temperatura: %.1fC %s\n", temp, avaliarTemperatura(temp)));
            sb.append(String.format("   - Umidade: %.1f%% %s\n", umidade, avaliarUmidade(umidade)));
            sb.append(String.format("   - Ruido: %.1f dB %s\n", ruido, avaliarRuido(ruido)));
            sb.append("\n");
        }
        
        rankingConforto.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        sb.append("RANKING DE CONFORTO (melhores localidades):\n");
        for (int i = 0; i < rankingConforto.size(); i++) {
            sb.append(String.format("   %d. %s (%.1f/100)\n", 
                i + 1, rankingConforto.get(i).getKey(), rankingConforto.get(i).getValue()));
        }
        
        return new Relatorio(
            Relatorio.TipoRelatorio.CONFORTO_AMBIENTAL,
            "Análise de Conforto Ambiental",
            sb.toString()
        );
    }
    
    private double calcularIQA(double pm25, double pm10, double co2) {
        double scorePM25 = Math.max(0, 100 - (pm25 / 150.0 * 100));
        double scorePM10 = Math.max(0, 100 - (pm10 / 250.0 * 100));
        double scoreCO2 = Math.max(0, 100 - ((co2 - 400) / 1600.0 * 100));
        return (scorePM25 + scorePM10 + scoreCO2) / 3.0;
    }
    
    private String classificarQualidadeAr(double iqa) {
        if (iqa >= 80) return "Boa";
        if (iqa >= 60) return "Moderada";
        if (iqa >= 40) return "Ruim";
        if (iqa >= 20) return "Pessima";
        return "Critica";
    }
    
    private double calcularIndiceConforto(double temp, double umidade, double ruido) {
        double scoreTemp = 100 - Math.abs(temp - 23) * 5;
        double scoreUmidade = 100 - Math.abs(umidade - 60) * 2;
        double scoreRuido = Math.max(0, 100 - ((ruido - 40) / 60.0 * 100));
        
        scoreTemp = Math.max(0, Math.min(100, scoreTemp));
        scoreUmidade = Math.max(0, Math.min(100, scoreUmidade));
        
        return (scoreTemp + scoreUmidade + scoreRuido) / 3.0;
    }
    
    private String classificarConforto(double indice) {
        if (indice >= 80) return "Excelente";
        if (indice >= 65) return "Bom";
        if (indice >= 50) return "Regular";
        return "Ruim";
    }
    
    private String avaliarTemperatura(double temp) {
        if (temp < 18) return "(Frio)";
        if (temp < 25) return "(Confortável)";
        if (temp < 30) return "(Quente)";
        return "(Muito quente)";
    }
    
    private String avaliarUmidade(double umidade) {
        if (umidade < 40) return "(Seco)";
        if (umidade < 70) return "(Confortável)";
        return "(Úmido)";
    }
    
    private String avaliarRuido(double ruido) {
        if (ruido < 55) return "(Silencioso)";
        if (ruido < 70) return "(Moderado)";
        if (ruido < 85) return "(Alto)";
        return "(Poluição sonora)";
    }
}

