#!/bin/bash

# API Gateway Deployment Script
# Deploys and configures the Fintech API Gateway infrastructure

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="fintech-system"
GATEWAY_TYPE="istio" # Options: istio, nginx, kubernetes-ingress
DOMAIN="api.fintech.com"
LOCAL_DOMAIN="api.fintech.local"
SSL_ENABLED=true

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_banner() {
    echo -e "${BLUE}"
    echo "=================================================================="
    echo "              FINTECH API GATEWAY DEPLOYMENT"
    echo "=================================================================="
    echo -e "${NC}"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed"
        exit 1
    fi
    
    # Check cluster connection
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi
    
    # Check specific gateway prerequisites
    case $GATEWAY_TYPE in
        "istio")
            if ! kubectl get crd gateways.networking.istio.io &> /dev/null; then
                log_error "Istio is not installed in the cluster"
                exit 1
            fi
            ;;
        "nginx"|"kubernetes-ingress")
            if ! kubectl get ingressclass nginx &> /dev/null; then
                log_error "NGINX Ingress Controller is not installed"
                exit 1
            fi
            ;;
    esac
    
    log_success "Prerequisites check passed"
}

create_namespace() {
    log_info "Creating namespace: $NAMESPACE"
    
    kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
    
    # Label namespace for Istio injection if using Istio
    if [ "$GATEWAY_TYPE" = "istio" ]; then
        kubectl label namespace $NAMESPACE istio-injection=enabled --overwrite
    fi
    
    log_success "Namespace $NAMESPACE created/updated"
}

generate_ssl_certificates() {
    log_info "Generating SSL certificates..."
    
    # Create temporary directory for certificates
    CERT_DIR=$(mktemp -d)
    
    # Generate private key
    openssl genrsa -out "$CERT_DIR/fintech.key" 2048
    
    # Generate certificate signing request
    cat > "$CERT_DIR/csr.conf" <<EOF
[req]
default_bits = 2048
prompt = no
distinguished_name = dn
req_extensions = v3_req

[dn]
C=US
ST=California
L=San Francisco
O=Fintech API
OU=Engineering
CN=$DOMAIN

[v3_req]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = $DOMAIN
DNS.2 = $LOCAL_DOMAIN
DNS.3 = *.fintech.com
DNS.4 = *.fintech.local
EOF
    
    # Generate certificate
    openssl req -new -key "$CERT_DIR/fintech.key" -out "$CERT_DIR/fintech.csr" -config "$CERT_DIR/csr.conf"
    openssl x509 -req -in "$CERT_DIR/fintech.csr" -signkey "$CERT_DIR/fintech.key" -out "$CERT_DIR/fintech.crt" -days 365 -extensions v3_req -extfile "$CERT_DIR/csr.conf"
    
    # Create Kubernetes secret
    kubectl create secret tls fintech-tls-secret \
        --cert="$CERT_DIR/fintech.crt" \
        --key="$CERT_DIR/fintech.key" \
        --namespace=$NAMESPACE \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Alternative secret name for different gateway types
    kubectl create secret tls fintech-api-tls \
        --cert="$CERT_DIR/fintech.crt" \
        --key="$CERT_DIR/fintech.key" \
        --namespace=$NAMESPACE \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Cleanup
    rm -rf "$CERT_DIR"
    
    log_success "SSL certificates created"
}

deploy_istio_gateway() {
    log_info "Deploying Istio Gateway configuration..."
    
    if [ ! -f "istio-gateway.yaml" ]; then
        log_error "istio-gateway.yaml not found"
        exit 1
    fi
    
    kubectl apply -f istio-gateway.yaml -n $NAMESPACE
    
    # Wait for gateway to be ready
    log_info "Waiting for Istio Gateway to be ready..."
    kubectl wait --for=condition=Ready gateway/fintech-gateway -n $NAMESPACE --timeout=300s
    
    log_success "Istio Gateway deployed successfully"
}

deploy_nginx_gateway() {
    log_info "Deploying NGINX Gateway configuration..."
    
    if [ ! -f "nginx-gateway.yaml" ]; then
        log_error "nginx-gateway.yaml not found"
        exit 1
    fi
    
    kubectl apply -f nginx-gateway.yaml -n $NAMESPACE
    
    # Wait for deployment to be ready
    log_info "Waiting for NGINX Gateway to be ready..."
    kubectl wait --for=condition=Available deployment/nginx-gateway -n $NAMESPACE --timeout=300s
    
    log_success "NGINX Gateway deployed successfully"
}

deploy_kubernetes_ingress() {
    log_info "Deploying Kubernetes Ingress configuration..."
    
    if [ ! -f "kubernetes-ingress.yaml" ]; then
        log_error "kubernetes-ingress.yaml not found"
        exit 1
    fi
    
    kubectl apply -f kubernetes-ingress.yaml -n $NAMESPACE
    
    # Wait for ingress to get an IP
    log_info "Waiting for Ingress to get an external IP..."
    timeout 300 bash -c 'until kubectl get ingress fintech-api-ingress -n '$NAMESPACE' -o jsonpath="{.status.loadBalancer.ingress[0].ip}" | grep -E "^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$"; do sleep 5; done' || true
    
    log_success "Kubernetes Ingress deployed successfully"
}

configure_dns() {
    log_info "Configuring DNS entries..."
    
    # Get external IP based on gateway type
    case $GATEWAY_TYPE in
        "istio")
            EXTERNAL_IP=$(kubectl get svc istio-ingressgateway -n istio-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
            ;;
        "nginx")
            EXTERNAL_IP=$(kubectl get svc nginx-gateway-service -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
            ;;
        "kubernetes-ingress")
            EXTERNAL_IP=$(kubectl get ingress fintech-api-ingress -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
            ;;
    esac
    
    if [ -n "$EXTERNAL_IP" ]; then
        log_success "External IP: $EXTERNAL_IP"
        log_info "Add the following entries to your DNS or /etc/hosts:"
        echo "$EXTERNAL_IP $DOMAIN"
        echo "$EXTERNAL_IP $LOCAL_DOMAIN"
    else
        log_warning "External IP not yet available. Check again later with:"
        case $GATEWAY_TYPE in
            "istio")
                echo "kubectl get svc istio-ingressgateway -n istio-system"
                ;;
            "nginx")
                echo "kubectl get svc nginx-gateway-service -n $NAMESPACE"
                ;;
            "kubernetes-ingress")
                echo "kubectl get ingress fintech-api-ingress -n $NAMESPACE"
                ;;
        esac
    fi
}

setup_monitoring() {
    log_info "Setting up gateway monitoring..."
    
    # Create ServiceMonitor for Prometheus scraping
    cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: fintech-gateway-monitor
  namespace: $NAMESPACE
  labels:
    app: fintech-gateway
spec:
  selector:
    matchLabels:
      app: nginx-gateway
  endpoints:
  - port: metrics
    path: /nginx_status
    interval: 30s
EOF
    
    log_success "Gateway monitoring configured"
}

run_health_checks() {
    log_info "Running health checks..."
    
    # Wait a bit for services to stabilize
    sleep 30
    
    # Check if services are running
    case $GATEWAY_TYPE in
        "istio")
            kubectl get gateway fintech-gateway -n $NAMESPACE
            kubectl get virtualservice fintech-api-routes -n $NAMESPACE
            ;;
        "nginx")
            kubectl get deployment nginx-gateway -n $NAMESPACE
            kubectl get service nginx-gateway-service -n $NAMESPACE
            ;;
        "kubernetes-ingress")
            kubectl get ingress fintech-api-ingress -n $NAMESPACE
            ;;
    esac
    
    # Test basic connectivity (if external IP is available)
    if [ -n "$EXTERNAL_IP" ]; then
        log_info "Testing gateway connectivity..."
        if curl -k -s -o /dev/null -w "%{http_code}" "https://$EXTERNAL_IP/health" | grep -q "200\|404"; then
            log_success "Gateway is responding to requests"
        else
            log_warning "Gateway may not be fully ready yet"
        fi
    fi
}

cleanup_old_resources() {
    log_info "Cleaning up old resources if they exist..."
    
    # Remove old deployments/services that might conflict
    kubectl delete deployment nginx-gateway -n $NAMESPACE --ignore-not-found=true
    kubectl delete service nginx-gateway-service -n $NAMESPACE --ignore-not-found=true
    kubectl delete ingress fintech-api-ingress -n $NAMESPACE --ignore-not-found=true
    kubectl delete gateway fintech-gateway -n $NAMESPACE --ignore-not-found=true
    kubectl delete virtualservice fintech-api-routes -n $NAMESPACE --ignore-not-found=true
    
    log_success "Cleanup completed"
}

print_deployment_info() {
    echo ""
    log_success "API Gateway deployment completed!"
    echo ""
    echo "Configuration:"
    echo "  Gateway Type: $GATEWAY_TYPE"
    echo "  Namespace: $NAMESPACE"
    echo "  Domain: $DOMAIN"
    echo "  Local Domain: $LOCAL_DOMAIN"
    echo "  SSL Enabled: $SSL_ENABLED"
    echo ""
    
    if [ -n "$EXTERNAL_IP" ]; then
        echo "External IP: $EXTERNAL_IP"
        echo ""
        echo "Test endpoints:"
        echo "  https://$DOMAIN/api/v1/auth/health"
        echo "  https://$DOMAIN/api/v1/account/health"
        echo "  https://$DOMAIN/api/v1/payments/health"
        echo ""
    fi
    
    echo "Useful commands:"
    echo "  kubectl get all -n $NAMESPACE"
    case $GATEWAY_TYPE in
        "istio")
            echo "  kubectl get gateway,virtualservice -n $NAMESPACE"
            ;;
        "nginx")
            echo "  kubectl logs -f deployment/nginx-gateway -n $NAMESPACE"
            ;;
        "kubernetes-ingress")
            echo "  kubectl describe ingress fintech-api-ingress -n $NAMESPACE"
            ;;
    esac
}

print_help() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --type TYPE         Gateway type: istio, nginx, kubernetes-ingress (default: istio)"
    echo "  --namespace NS      Kubernetes namespace (default: fintech-system)"
    echo "  --domain DOMAIN     Primary domain (default: api.fintech.com)"
    echo "  --local-domain DOM  Local domain (default: api.fintech.local)"
    echo "  --no-ssl           Disable SSL/TLS"
    echo "  --cleanup          Cleanup old resources before deployment"
    echo "  --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Deploy with default settings (Istio)"
    echo "  $0 --type nginx --namespace prod      # Deploy NGINX gateway in prod namespace"
    echo "  $0 --type kubernetes-ingress --no-ssl # Deploy K8s Ingress without SSL"
}

# Main execution
main() {
    local cleanup_first=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --type)
                GATEWAY_TYPE="$2"
                shift 2
                ;;
            --namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            --domain)
                DOMAIN="$2"
                shift 2
                ;;
            --local-domain)
                LOCAL_DOMAIN="$2"
                shift 2
                ;;
            --no-ssl)
                SSL_ENABLED=false
                shift
                ;;
            --cleanup)
                cleanup_first=true
                shift
                ;;
            --help)
                print_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                print_help
                exit 1
                ;;
        esac
    done
    
    # Validate gateway type
    if [[ ! "$GATEWAY_TYPE" =~ ^(istio|nginx|kubernetes-ingress)$ ]]; then
        log_error "Invalid gateway type: $GATEWAY_TYPE"
        log_error "Valid types: istio, nginx, kubernetes-ingress"
        exit 1
    fi
    
    print_banner
    
    log_info "Deploying API Gateway with configuration:"
    log_info "  Type: $GATEWAY_TYPE"
    log_info "  Namespace: $NAMESPACE"
    log_info "  Domain: $DOMAIN"
    log_info "  SSL Enabled: $SSL_ENABLED"
    
    # Execute deployment steps
    check_prerequisites
    
    if [ "$cleanup_first" = true ]; then
        cleanup_old_resources
    fi
    
    create_namespace
    
    if [ "$SSL_ENABLED" = true ]; then
        generate_ssl_certificates
    fi
    
    # Deploy based on gateway type
    case $GATEWAY_TYPE in
        "istio")
            deploy_istio_gateway
            ;;
        "nginx")
            deploy_nginx_gateway
            ;;
        "kubernetes-ingress")
            deploy_kubernetes_ingress
            ;;
    esac
    
    configure_dns
    setup_monitoring
    run_health_checks
    print_deployment_info
}

# Run main function with all arguments
main "$@"
