package com.example.lancamapp

object OnvifConstants {
    // This is a "Probe" message defined by the ONVIF standard
    const val DISCOVERY_PROBE = """
        <?xml version="1.0" encoding="utf-8"?>
        <Envelope xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns="http://www.w3.org/2003/05/soap-envelope">
            <Header>
                <wsa:MessageID xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">uuid:56b55e69-2342-4543-8555-523232665675</wsa:MessageID>
                <wsa:To xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
                <wsa:Action xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>
            </Header>
            <Body>
                <Probe xmlns="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                    <Types>tds:Device</Types>
                    <Scopes />
                </Probe>
            </Body>
        </Envelope>
    """
}