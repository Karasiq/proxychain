# Proxychain
Proxy-chaining SOCKS4/SOCKS5/HTTPS server

# Configuration
Configuration stored in file `proxychain.conf`

Example: 
```
proxyChain {
  host = "0.0.0.0" // Listen IP
  port = 1080 // Listen port
  
  // Firewall settings:
  allowedRanges = [
    // IP ranges whitelist
  ]
  blockedRanges = [
    // IP ranges blacklist
  ]
  
  allowedPorts = [
    // Ports whitelist
  ]
  blockedPorts = [
    // Ports blacklist
  ]
  
  allowedHosts = [
    // Hosts whitelist
  ]
  blockedHosts = [
    // Hosts blacklist
  ]
  
  // Proxy servers here:
  entry {
    // Default hop settings
    hops = 0
    randomize = false
    
    proxies = [
      "http://user:password@proxy1.com:8080"  // First server in chain
    ]
  }
  middle {
    // Custom hop settings
    hops = 3
    randomize = true
    
    proxies = [
      // Intermediate proxies here
    ]
  }
  exit {
    // Hop settings omitted - using default
  
    proxies = [
      "socks://proxy2.com:1080"  // Last server in chain 
    ]
  }
}
```