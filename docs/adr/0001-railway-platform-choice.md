# ADR 0001: Railway PaaS for Deployment Platform

## Status

Accepted

## Context

Skatemap Live requires a publicly accessible deployment for demonstration and testing purposes. The application is a WebSocket-based real-time location streaming service built with Scala Play Framework, requiring:

- HTTP/HTTPS endpoint for REST API
- WebSocket support for streaming connections
- Container deployment capability (Docker)
- Automatic SSL/TLS certificate management
- Minimal operational overhead for a prototype/demo application

The primary goal is to demonstrate the application's functionality without investing significant time in infrastructure setup and maintenance. This is a prototype application, not a production service requiring high availability guarantees.

## Decision

Deploy to **Railway PaaS** as the initial deployment platform.

## Consequences

### Benefits

**Zero Infrastructure Management**
- No VPC configuration
- No load balancer setup
- No security group rules
- No SSL certificate provisioning
- No server provisioning or patching

**Cost Efficiency**
- Free tier sufficient for demonstration purposes
- No upfront costs
- Pay only for actual usage beyond free tier

**Development Velocity**
- Deployment via git push or direct Docker image
- Automatic HTTPS with custom domains
- Built-in logging and metrics
- Estimated time saved: 8-12 hours vs IaaS setup

**Migration Path**
- Application is containerised (Docker)
- No vendor lock-in on application layer
- Can migrate to ECS/Fargate or Kubernetes if scale requirements emerge
- Clean separation of business logic from infrastructure

### Trade-offs

**Limited Control**
- Cannot fine-tune infrastructure (network, compute, storage)
- Dependent on Railway's platform reliability
- Limited customisation of deployment topology

**Scaling Constraints**
- Free tier has bandwidth and compute limits
- May require migration if traffic exceeds PaaS capabilities
- Not suitable for high-availability production requirements

**Vendor Dependency**
- Platform-specific configuration (railway.toml)
- Migration requires effort if Railway becomes unsuitable

## Alternatives Considered

### AWS ECS/Fargate (Managed Container Platform)

**Evaluated**: Managed container orchestration on AWS.

**Rejected because**:
- Requires VPC setup (subnets, route tables, internet gateway)
- Requires Application Load Balancer configuration
- Requires security group and IAM role setup
- Requires manual SSL certificate provisioning (ACM)
- Estimated setup time: 4-6 hours for experienced users, 8-12 hours for first-time setup
- Ongoing operational overhead (monitoring, updates, cost management)
- Overkill for a prototype application

**When to reconsider**: If application requires multi-region deployment, tight AWS service integration, or enterprise compliance requirements.

### AWS EC2 (Infrastructure as a Service)

**Evaluated**: Direct virtual machine deployment on AWS.

**Rejected because**:
- All VPC/networking setup required (as per ECS)
- Manual server provisioning and configuration
- Manual Docker installation and orchestration
- Manual SSL certificate setup (Let's Encrypt or ACM)
- Manual security patching and updates
- Manual deployment automation setup
- Estimated setup time: 8-12 hours minimum
- Highest operational burden

**When to reconsider**: If application requires specific hardware, custom kernel modules, or complete infrastructure control.

### Kubernetes (Self-Managed Container Orchestration)

**Evaluated**: Container orchestration on managed Kubernetes (EKS, GKE, AKS) or self-hosted.

**Rejected because**:
- Significant complexity for a single-service application
- Requires cluster setup and management (even on managed platforms)
- Requires ingress controller configuration for HTTPS
- Requires YAML manifests for deployments, services, ingress
- Estimated setup time: 12-16 hours for managed K8s, 20+ hours for self-hosted
- Ongoing operational overhead (cluster upgrades, node management)
- Massive overkill for a prototype

**When to reconsider**: If application grows to microservices architecture, requires advanced deployment strategies (canary, blue-green), or needs multi-cloud portability.

### Fly.io (Alternative PaaS)

**Evaluated**: Similar PaaS platform to Railway.

**Not selected because**:
- Railway's free tier is sufficient for current needs
- Railway's developer experience is well-documented
- No significant advantage over Railway for this use case

**When to reconsider**: If Railway pricing becomes unfavourable or platform reliability issues emerge.

### Render (Alternative PaaS)

**Evaluated**: Similar PaaS platform to Railway.

**Not selected because**:
- Railway selected first; no compelling reason to switch
- Similar feature set and pricing model

**When to reconsider**: If Railway pricing becomes unfavourable or platform reliability issues emerge.

## Comparison Matrix

| Factor | Railway PaaS | Fly.io/Render | AWS ECS/Fargate | AWS EC2 | Kubernetes |
|--------|--------------|---------------|-----------------|---------|------------|
| **Setup Time** | < 1 hour | < 1 hour | 4-12 hours | 8-12 hours | 12-20 hours |
| **Infrastructure Config** | None | None | High (VPC, ALB, SG) | Very High | Very High |
| **SSL/TLS** | Automatic | Automatic | Manual (ACM) | Manual | Manual (cert-manager) |
| **Free Tier** | Yes | Yes | Limited | No | No (cluster costs) |
| **Operational Overhead** | Minimal | Minimal | Moderate | High | Very High |
| **Scaling Limits** | Moderate | Moderate | High | Very High | Very High |
| **Vendor Lock-in** | Low (Docker) | Low (Docker) | Medium (AWS) | Medium (AWS) | Low (portable) |
| **Migration Effort** | Low (Docker portable) | Low (Docker portable) | Medium (AWS-specific config) | Medium (infrastructure rebuild) | Low (K8s portable) |
| **Cost (Demo)** | $0 | $0 | $15-30/month | $10-20/month | $50-100/month |
| **Best For** | Prototypes, demos | Prototypes, demos | Production apps | Custom infrastructure | Microservices |

## Notes

This decision prioritises speed of deployment and minimal operational overhead for a demonstration application. The containerised architecture ensures the application can migrate to more sophisticated infrastructure if requirements change.

Railway deployment implemented in:
- `railway.toml`: Platform configuration
- `Dockerfile.railway`: Railway-optimised Docker build with cache mounts
- Environment variable configuration in `application.conf`
