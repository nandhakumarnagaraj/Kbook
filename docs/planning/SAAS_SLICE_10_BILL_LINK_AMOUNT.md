# KhanaBook SaaS — slice 10: stable bill link amounts

Bill payment links now send the exact bill total using a dot decimal separator, independent of the server's locale. The server rejects a missing, non-positive, or over-precise bill total rather than rounding it while creating a gateway link. The bill ID portion of the merchant transaction ID is also formatted with a fixed locale.

Verification: `EasebuzzIntegrationTest` creates a bill link while the default locale is German and checks that Easebuzz receives `1000.00`. The focused integration suite passed.
