# ============================================
# STAGE 1: Build
# ============================================
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copiar código fonte
COPY src/ ./src/

# Criar diretório de saída
RUN mkdir -p out

# Compilar todos os arquivos Java
RUN javac -d out $(find src -name "*.java")

# ============================================
# STAGE 2: Runtime
# ============================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copiar classes compiladas
COPY --from=builder /app/out ./classes

# Criar diretórios de dados
RUN mkdir -p /app/borda_dados /app/datacenter_dados/leituras /app/datacenter_dados/relatorios

# Variável de ambiente para escolher o componente
ENV COMPONENT=datacenter

# Script de entrada que escolhe qual componente executar
COPY <<'EOF' /app/entrypoint.sh
#!/bin/bash
set -e

cd /app

case "$COMPONENT" in
    datacenter)
        echo "Iniciando Servidor Datacenter..."
        exec java -cp classes datacenter.ServidorDatacenter
        ;;
    borda)
        echo "Iniciando Servidor Borda..."
        exec java -cp classes borda.ServidorBorda
        ;;
    dispositivo)
        echo "Iniciando Dispositivo Sensor..."
        exec java -cp classes MainDispositivo
        ;;
    cliente)
        echo "Iniciando Cliente..."
        exec java -cp classes MainCliente
        ;;
    firewall-filtro)
        echo "Iniciando Firewall Filtro de Pacotes..."
        exec java -cp classes seguranca.FirewallFiltro
        ;;
    firewall-proxy)
        echo "Iniciando Firewall Proxy..."
        exec java -cp classes seguranca.FirewallProxy
        ;;
    ids)
        echo "Iniciando Sistema de Detecção de Intrusão (IDS)..."
        exec java -cp classes seguranca.IDS
        ;;
    *)
        echo "Componente desconhecido: $COMPONENT"
        echo "Componentes válidos: datacenter, borda, dispositivo, cliente, firewall-filtro, firewall-proxy, ids"
        exit 1
        ;;
esac
EOF

RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]

