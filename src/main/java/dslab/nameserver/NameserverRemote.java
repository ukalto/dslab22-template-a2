package dslab.nameserver;

import java.io.PrintStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NameserverRemote implements INameserverRemote {
    private final ConcurrentHashMap<String, INameserverRemote> nameServers;
    private final ConcurrentHashMap<String, String> mailServers;
    private final String domain;

    public NameserverRemote(String domain) {
        this.nameServers = new ConcurrentHashMap<>();
        this.mailServers = new ConcurrentHashMap<>();
        this.domain = domain;
    }

    private String checkDomain(String domain) throws InvalidDomainException {
        if (domain == null || domain.isEmpty() || !domain.matches("[A-Za-z\\.]*")) {
            throw new InvalidDomainException("Domain: " + domain + " is invalid");
        }
        return domain.toLowerCase();
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        domain = checkDomain(domain);
        synchronized (nameServers) {
            if (domain.contains(".")) {
                // Domain is sub domain
                String topLevelDomain = domain.substring(domain.lastIndexOf("\\.") + 1);
                if (nameServers.containsKey(topLevelDomain)) {
                    nameServers.get(topLevelDomain).registerNameserver(domain.substring(domain.lastIndexOf('.') + 1), nameserver);
                } else {
                    throw new InvalidDomainException("Namesever for domain " + topLevelDomain + " doesn't exist");
                }
            } else {
                // no sub domain -> name server has to be added here
                if (!nameServers.containsKey(domain)) {
                    nameServers.put(domain, nameserver);
                } else {
                    throw new AlreadyRegisteredException("Nameserver with the domain: " + domain + " is already registered");
                }
            }
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        domain = checkDomain(domain);
        String mailServerName = domain.substring(0, domain.indexOf('.')), zone = domain.substring(mailServerName.length() + 1);
        if (zone.contains(".")) {
            zone = zone.substring(0, zone.indexOf('.'));
        }
        if (this.domain != null && this.domain.equals(zone)) {
            synchronized (mailServers) {
                if (this.mailServers.containsKey(mailServerName)) {
                    throw new AlreadyRegisteredException("Mailserver: " + domain + " already registered");
                } else {
                    mailServers.put(mailServerName, address);
                }
            }
        } else {
            getNameserver(zone).registerMailboxServer(domain, address);
        }

    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        synchronized (nameServers) {
            if (!nameServers.containsKey(zone.toLowerCase())) {
                throw new RemoteException("Nameserver for zone: " + zone.toLowerCase() + " not found");
            }
            return nameServers.get(zone.toLowerCase());
        }
    }

    @Override
    public String lookup(String username) throws RemoteException {
        synchronized (mailServers) {
            if (!mailServers.containsKey(username.toLowerCase())) {
                throw new RemoteException("Mailserver with the name: " + username.toLowerCase() + " not found");
            }
            return mailServers.get(username.toLowerCase());
        }
    }

    public void printNameServers(PrintStream outputStream) {
        synchronized (nameServers) {
            AtomicInteger amount = new AtomicInteger(1);
            List<String> domains = new ArrayList<>(nameServers.keySet());
            Collections.sort(domains);
            for (String d : domains) {
                outputStream.printf("%d. %s%n", amount.getAndIncrement(), d);
            }
        }
    }

    public void printMailServers(PrintStream outputStream) {
        synchronized (mailServers) {
            AtomicInteger amount = new AtomicInteger(1);
            List<String> domains = new ArrayList<>(nameServers.keySet());
            Collections.sort(domains);
            for (String d : domains) {
                outputStream.printf("%d. %s %s%n", amount.getAndIncrement(), d, mailServers.get(d));
            }
        }
    }
}

