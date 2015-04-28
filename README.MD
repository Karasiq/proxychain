# Proxychain
Proxy-chaining SOCKS4/SOCKS5/HTTPS server

# Configuration
Configuration through `proxychain.coffee` script:
```coffeescript
entry = [
  "socks://localhost:9050" # TOR
]

loadMiddle = () ->
  socks = ProxySource.fromURL("http://localhost:36800/proxylist.txt?list=public&protocol=socks&alive=true&latency=1500").map((ip) -> "socks://" + ip)
  https = ProxySource.fromURL("http://localhost:36800/proxylist.txt?list=public&protocol=https&alive=true&latency=1500")
  proxies = socks.concat(http)
  Logger.info("Loaded {} proxies", proxies.length)
  proxies

middle = loadMiddle()
exit = [
  "http://exit-proxy.com:8080"
]

isHttp = (address) ->
  address.getPort() == 80 || address.getPort() == 443

return {
  connectionIsAllowed: (address) -> isHttp(address) && DefaultFirewall.connectionIsAllowed(address)
  proxyChainsFor: (address) -> ChainBuilder.chainsFrom(2, entry, ChainBuilder.hops(middle, 3), exit)
}
```

Configuration through `proxychain.conf`:
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