# API Gateway Configuration

This directory contains the complete API Gateway infrastructure for the Fintech API platform, supporting multiple gateway implementations with comprehensive routing, security, and traffic management.

## Architecture Overview

The API Gateway serves as the single entry point for all client requests, providing:
- **Unified API Surface**: Single endpoint for all microservices
- **Authentication & Authorization**: JWT token validation and RBAC
- **Rate Limiting & Throttling**: Protection against abuse and overload
- **Load Balancing**: Traffic distribution across service instances
- **SSL/TLS Termination**: Secure communication handling
- **Request/Response Transformation**: Protocol and format handling
- **Monitoring & Observability**: Comprehensive metrics and tracing

## Gateway Implementations

### 1. Istio Service Mesh (`istio-gateway.yaml`)
**Production-recommended implementation** providing advanced traffic management:

#### Features:
- **Advanced Traffic Management**: Canary deployments, A/B testing, traffic splitting
- **Security**: mTLS, JWT validation, authorization policies
- **Observability**: Distributed tracing, metrics, logging
- **Resilience**: Circuit breakers, retries, timeouts, fault injection

#### Components:
```yaml
Gateway:           # External traffic entry point
VirtualService:    # Route configuration and traffic rules  
DestinationRule:   # Load balancing and circuit breakers
AuthorizationPolicy: # RBAC and security policies
RequestAuthentication: # JWT validation
```

#### Traffic Routing:
- **Authentication**: `/api/v1/auth` → Auth Service
- **Payments**: `/api/v1/payments` → Payment Service (60s timeout)
- **Fraud Detection**: `/api/v1/fraud` → Fraud Service (20s timeout)
- **Admin**: `/api/v1/admin` → Admin Dashboard (Role-based access)
- **Mobile**: `/api/v1/mobile` → Mobile SDK Service
- **Reports**: `/api/v1/reports` → Reporting Service (120s timeout)

### 2. NGINX Gateway (`nginx-gateway.yaml`)
**High-performance HTTP proxy** with enterprise-grade features:

#### Features:
- **High Performance**: 4096 worker connections, optimized buffering
- **Advanced Rate Limiting**: Multiple zones for different endpoints
- **SSL/TLS**: Modern cipher suites, HSTS, security headers
- **Health Checks**: Upstream monitoring and failover
- **Caching**: Static content caching and proxy buffering

#### Rate Limiting Zones:
```nginx
general:  100 requests/minute (burst: 10)
auth:     10 requests/minute (burst: 5)  
payment:  20 requests/minute (burst: 5)
admin:    30 requests/minute (burst: 10)
```

#### Security Headers:
- **HSTS**: Strict-Transport-Security
- **CSP**: Content-Security-Policy
- **XSS Protection**: X-XSS-Protection
- **Frame Options**: X-Frame-Options DENY

### 3. Kubernetes Ingress (`kubernetes-ingress.yaml`)
**Cloud-native solution** with automatic SSL and load balancer integration:

#### Features:
- **Automatic SSL**: cert-manager integration with Let's Encrypt
- **Cloud Integration**: Native LoadBalancer service type
- **Path-based Routing**: Flexible routing configuration
- **Error Handling**: Custom error pages and fallback responses

#### Annotations:
```yaml
nginx.ingress.kubernetes.io/rate-limit: "100"
nginx.ingress.kubernetes.io/ssl-redirect: "true"
nginx.ingress.kubernetes.io/cors-allow-origin: "https://app.fintech.com"
cert-manager.io/cluster-issuer: "letsencrypt-prod"
```

## Deployment

### Prerequisites
- Kubernetes cluster (1.25+)
- kubectl configured
- Gateway-specific requirements:
  - **Istio**: Istio control plane installed
  - **NGINX**: NGINX Ingress Controller
  - **Kubernetes**: Ingress controller (NGINX/Traefik/etc.)

### Quick Start
```bash
# Deploy Istio Gateway (recommended)
./deploy-gateway.sh

# Deploy NGINX Gateway
./deploy-gateway.sh --type nginx

# Deploy Kubernetes Ingress
./deploy-gateway.sh --type kubernetes-ingress

# Deploy with custom configuration
./deploy-gateway.sh --type istio --namespace production --domain api.fintech.com
```

### Advanced Deployment Options
```bash
# Cleanup existing resources
./deploy-gateway.sh --cleanup --type nginx

# Deploy without SSL (development)
./deploy-gateway.sh --no-ssl --namespace dev

# Custom domain configuration
./deploy-gateway.sh --domain api.myfintech.com --local-domain api.local
```

## Security Configuration

### Authentication Flow
1. **Public Endpoints**: Login, registration, health checks (no auth required)
2. **JWT Validation**: All authenticated endpoints validate JWT tokens
3. **Role-based Access**: Admin endpoints require ADMIN/SUPER_ADMIN roles
4. **Service-to-Service**: Internal communication with service principals

### Rate Limiting Strategy
| Endpoint Type | Rate Limit | Burst | Purpose |
|---------------|------------|-------|---------|
| Authentication | 10/min | 5 | Prevent brute force |
| Payment Initiation | 20/min | 5 | Prevent abuse |
| General API | 100/min | 10 | Normal operations |
| Admin Dashboard | 30/min | 10 | Administrative access |

### Security Headers
```http
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'
```

## Traffic Management

### Load Balancing
- **Algorithm**: Least connections with session affinity
- **Health Checks**: Active health monitoring of upstream services
- **Failover**: Automatic traffic redirection on service failures
- **Circuit Breakers**: Protection against cascading failures

### Timeout Configuration
| Service | Timeout | Retries | Rationale |
|---------|---------|---------|-----------|
| Authentication | 30s | 3 | Quick auth validation |
| Account Service | 30s | 3 | Fast account operations |
| Payment Service | 60s | 2 | Complex payment processing |
| Fraud Detection | 20s | 3 | Real-time analysis |
| Reporting | 120s | 2 | Report generation |
| Admin Dashboard | 30s | 3 | Management operations |

### Canary Deployments (Istio)
```yaml
# 90% stable, 10% canary traffic split
VirtualService:
  - destination: payment-service
    subset: stable
    weight: 90
  - destination: payment-service  
    subset: canary
    weight: 10
```

## Monitoring & Observability

### Metrics Collection
- **Request Metrics**: Rate, latency, error rate by endpoint
- **Upstream Metrics**: Service health, response times
- **Security Metrics**: Authentication failures, rate limit hits
- **Infrastructure Metrics**: Connection pools, memory usage

### Health Checks
```bash
# Gateway health
curl -k https://api.fintech.com/health

# Service-specific health
curl -k https://api.fintech.com/actuator/health

# Admin monitoring endpoint
curl -k https://api.fintech.com:8080/nginx_status
```

### Distributed Tracing
- **Jaeger Integration**: Request tracing across all services
- **Correlation IDs**: Request tracking through the entire system
- **Performance Analysis**: Bottleneck identification and optimization

## DNS Configuration

### Production Setup
```dns
api.fintech.com.    A    <EXTERNAL_IP>
*.fintech.com.      A    <EXTERNAL_IP>
```

### Local Development
```bash
# Add to /etc/hosts
<EXTERNAL_IP> api.fintech.local
<EXTERNAL_IP> api.fintech.com
```

### SSL Certificate Management
```bash
# Generate development certificates
./deploy-gateway.sh --type nginx

# Production certificates (Let's Encrypt)
./deploy-gateway.sh --type kubernetes-ingress --domain api.fintech.com
```

## Testing

### Functional Testing
```bash
# Authentication flow
curl -X POST https://api.fintech.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'

# Protected endpoint
curl -H "Authorization: Bearer <token>" \
  https://api.fintech.com/api/v1/account/profile

# Admin endpoint
curl -H "Authorization: Bearer <admin_token>" \
  https://api.fintech.com/api/v1/admin/dashboard/metrics
```

### Load Testing
```bash
# Use Apache Bench for simple load testing
ab -n 1000 -c 10 -H "Authorization: Bearer <token>" \
  https://api.fintech.com/api/v1/account/profile

# Or use k6 for advanced scenarios
k6 run --vus 50 --duration 5m load-test.js
```

### Security Testing
```bash
# Rate limiting test
for i in {1..20}; do
  curl -w "%{http_code}\n" -o /dev/null -s \
    https://api.fintech.com/api/v1/auth/login
done

# SSL/TLS configuration test
nmap --script ssl-enum-ciphers -p 443 api.fintech.com
```

## Troubleshooting

### Common Issues

#### 1. Gateway Not Responding
```bash
# Check gateway pods
kubectl get pods -n fintech-system -l app=nginx-gateway

# Check service endpoints
kubectl get svc nginx-gateway-service -n fintech-system

# View logs
kubectl logs -f deployment/nginx-gateway -n fintech-system
```

#### 2. SSL Certificate Issues
```bash
# Check certificate secret
kubectl get secret fintech-tls-secret -n fintech-system -o yaml

# Verify certificate validity
openssl x509 -in <cert_file> -text -noout
```

#### 3. Service Discovery Problems
```bash
# Check service registration
kubectl get svc -n fintech-system

# Test internal connectivity
kubectl run debug --rm -i --tty --image=nicolaka/netshoot -- /bin/bash
nslookup payment-service.fintech-system.svc.cluster.local
```

#### 4. Rate Limiting Issues
```bash
# Check NGINX rate limit status
kubectl exec -it deployment/nginx-gateway -n fintech-system -- \
  cat /var/log/nginx/error.log | grep "rate limit"
```

### Debug Commands
```bash
# Gateway status
kubectl get gateway,virtualservice,destinationrule -n fintech-system

# Check Istio proxy configuration
istioctl proxy-config cluster <pod-name> -n fintech-system

# View NGINX configuration
kubectl exec -it deployment/nginx-gateway -n fintech-system -- \
  nginx -T
```

## Performance Optimization

### NGINX Tuning
```nginx
worker_processes auto;           # Match CPU cores
worker_connections 4096;         # High connection limit
keepalive_timeout 65;           # Connection reuse
client_max_body_size 10M;       # Request size limit
```

### Istio Optimization
```yaml
connectionPool:
  tcp:
    maxConnections: 100          # Connection pool limit
  http:
    http1MaxPendingRequests: 50  # Queue limit
    maxRequestsPerConnection: 10  # Connection reuse
```

### Caching Strategy
- **Static Assets**: 1 hour cache TTL
- **API Responses**: No caching (dynamic data)
- **Health Checks**: No caching
- **Authentication**: No caching (security)

## Production Considerations

### High Availability
- **Multiple Replicas**: 3+ gateway instances
- **Anti-Affinity**: Distribute across nodes/zones
- **Health Checks**: Automatic failure detection
- **Circuit Breakers**: Prevent cascade failures

### Security Hardening
- **Regular Updates**: Keep gateway components updated
- **Security Scanning**: Regular vulnerability assessments
- **Access Logs**: Comprehensive request logging
- **Intrusion Detection**: Monitor for suspicious patterns

### Capacity Planning
- **Connection Limits**: Based on expected load
- **Rate Limits**: Protect against abuse
- **Resource Limits**: CPU/Memory constraints
- **Scaling**: Horizontal pod autoscaling

## Contact Information

For questions about API Gateway configuration:
- **Team Lead**: Principal Software Engineer
- **Documentation**: `/docs/infrastructure/api-gateway.md`
- **Issues**: Create GitHub issue with `api-gateway` label
- **Emergency**: Follow incident response procedures
