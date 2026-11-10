"""Resolve only the known UAT hostname when the local QA executor lacks DNS.

The URL, Host header, and TLS server name remain unchanged. No TLS check is disabled.
"""

import socket

_original = socket.getaddrinfo


def _uat_getaddrinfo(host, port, *args, **kwargs):
    if host == "lawdesk.167-86-90-174.sslip.io":
        host = "167.86.90.174"
    return _original(host, port, *args, **kwargs)


socket.getaddrinfo = _uat_getaddrinfo
