package com.khanabook.saas.entity;

import com.khanabook.saas.sync.entity.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurantprofiles", uniqueConstraints = {
		@UniqueConstraint(name = "restaurantprofiles_restaurant_id_device_id_local_id_key", columnNames = { "restaurant_id", "device_id", "local_id" }) }, indexes = {
				@Index(name = "idx_restaurantprofiles_tenant_updated", columnList = "restaurant_id, updated_at"),
				@Index(name = "idx_restaurantprofiles_device", columnList = "restaurant_id, device_id, local_id"),
				@Index(name = "idx_restaurantprofiles_whatsapp_number", columnList = "whatsapp_number") })
@Getter
@Setter
public class RestaurantProfile extends BaseSyncEntity {

	@Column(name = "shop_name")
	private String shopName;

	@Column(name = "shop_address")
	private String shopAddress;

	@Column(name = "whatsapp_number")
	private String whatsappNumber;

	@Column(name = "email")
	private String email;

	@Column(name = "logo_path")
	private String logoPath;

	@Column(name = "fssai_number")
	private String fssaiNumber;

	@Column(name = "email_invoice_consent")
	private Boolean emailInvoiceConsent;

	@Column(name = "country")
	private String country;

	@Column(name = "gst_enabled")
	private Boolean gstEnabled;

	@Column(name = "gstin")
	private String gstin;

	@Column(name = "is_tax_inclusive")
	private Boolean isTaxInclusive;

	@Column(name = "gst_percentage", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal gstPercentage;

	@Column(name = "custom_tax_name")
	private String customTaxName;

	@Column(name = "custom_tax_number")
	private String customTaxNumber;

	@Column(name = "custom_tax_percentage", columnDefinition = "NUMERIC(12,2)")
	private java.math.BigDecimal customTaxPercentage;

	@Column(name = "currency")
	private String currency;

	@Column(name = "upi_enabled")
	private Boolean upiEnabled;

	@Column(name = "upi_qr_path")
	private String upiQrPath;

	@Column(name = "upi_handle")
	private String upiHandle;

	@Column(name = "upi_mobile")
	private String upiMobile;

	@Column(name = "cash_enabled")
	private Boolean cashEnabled;

	@Column(name = "pos_enabled")
	private Boolean posEnabled;

	@Column(name = "zomato_enabled")
	private Boolean zomatoEnabled;

	@Column(name = "swiggy_enabled")
	private Boolean swiggyEnabled;

	@Column(name = "own_website_enabled")
	private Boolean ownWebsiteEnabled;

	@Column(name = "printer_enabled")
	private Boolean printerEnabled;

	@Column(name = "printer_name")
	private String printerName;

	@Column(name = "printer_mac")
	private String printerMac;

	@Column(name = "kitchen_printer_enabled")
	private Boolean kitchenPrinterEnabled;

	@Column(name = "kitchen_printer_name")
	private String kitchenPrinterName;

	@Column(name = "kitchen_printer_mac")
	private String kitchenPrinterMac;

	@Column(name = "kitchen_printer_paper_size")
	private String kitchenPrinterPaperSize;

	@Column(name = "paper_size")
	private String paperSize;

	@Column(name = "auto_print_on_success")
	private Boolean autoPrintOnSuccess;

	@Column(name = "include_logo_in_print")
	private Boolean includeLogoInPrint;

	@Column(name = "print_customer_whatsapp")
	private Boolean printCustomerWhatsapp;

	@Column(name = "daily_order_counter")
	private Long dailyOrderCounter;

	@Column(name = "lifetime_order_counter")
	private Long lifetimeOrderCounter;

	@Column(name = "last_reset_date")
	private String lastResetDate;

	@Column(name = "session_timeout_minutes")
	private Integer sessionTimeoutMinutes;

	@Column(name = "timezone")
	private String timezone = "Asia/Kolkata";

	@Column(name = "review_url")
	private String reviewUrl;

	@Column(name = "show_branding")
	private Boolean showBranding = true;

	@Column(name = "mask_customer_phone")
	private Boolean maskCustomerPhone = true;

	@Column(name = "last_reset_date_proper")
	private java.time.LocalDate lastResetDateProper;

	@jakarta.persistence.PrePersist
	@jakarta.persistence.PreUpdate
	public void syncDates() {
		if (lastResetDate != null && !lastResetDate.isEmpty()) {
			try {
				lastResetDateProper = java.time.LocalDate.parse(lastResetDate);
			} catch (Exception e) {
				// ignore parsing errors
			}
		}
	}
}
