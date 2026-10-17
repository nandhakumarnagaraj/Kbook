package com.khanabook.lite.pos.test.util

object TestData {

    object ValidCredentials {
        const val PHONE = "9876543210"
        const val PASSWORD = "Test@1234"
        const val NAME = "Test User"
        const val EMAIL = "test@example.com"
    }

    object InvalidCredentials {
        const val PHONE = "9876543210"
        const val WRONG_PASSWORD = "wrongpass"
        const val EMPTY_PHONE = ""
        const val EMPTY_PASSWORD = ""
        const val SHORT_PASSWORD = "123"
        const val LONG_PHONE = "987654321012345"
        const val INVALID_PHONE_FORMAT = "12345"
        const val SPECIAL_CHARS_PASSWORD = "pass@word!"
    }

    object MenuItems {
        const val BURGER = "Burger"
        const val PIZZA = "Pizza"
        const val PASTA = "Pasta"
        const val COKE = "Coke"
        const val COFFEE = "Coffee"
        const val BIRYANI = "Biryani"
        const val NAAN = "Naan"
        const val CURRY = "Curry"
        
        val POPULAR_ITEMS = listOf(BURGER, PIZZA, PASTA, COKE, COFFEE)
        
        val ALL_ITEMS = listOf(BURGER, PIZZA, PASTA, COKE, COFFEE, BIRYANI, NAAN, CURRY)
    }

    object Categories {
        const val STARTERS = "Starters"
        const val MAIN_COURSE = "Main Course"
        const val BEVERAGES = "Beverages"
        const val DESSERTS = "Desserts"
        
        val ALL = listOf(STARTERS, MAIN_COURSE, BEVERAGES, DESSERTS)
    }

    object BillAmounts {
        const val AMOUNT_100 = "100"
        const val AMOUNT_250 = "250"
        const val AMOUNT_500 = "500"
        const val AMOUNT_1000 = "1000"
        const val AMOUNT_5000 = "5000"
        
        const val PRICE_BURGER = 150.00
        const val PRICE_PIZZA = 250.00
        const val PRICE_COKE = 50.00
        const val PRICE_COFFEE = 80.00
        
        val SINGLE_ITEM_TOTAL = PRICE_BURGER
        val TWO_ITEMS_TOTAL = PRICE_BURGER + PRICE_PIZZA
        val FULL_ORDER_TOTAL = PRICE_BURGER + PRICE_PIZZA + PRICE_COKE
    }

    object DiscountCodes {
        const val VALID_10_PERCENT = "SAVE10"
        const val VALID_20_PERCENT = "SAVE20"
        const val VALID_50_PERCENT = "FLAT50"
        const val INVALID_CODE = "INVALID"
        const val EXPIRED_CODE = "EXPIRED2020"
        const val CASE_SENSITIVE = "save10"
    }

    object OrderStatuses {
        const val PENDING = "Pending"
        const val PREPARING = "Preparing"
        const val READY = "Ready"
        const val COMPLETED = "Completed"
        const val CANCELLED = "Cancelled"
    }

    object PaymentMethods {
        const val CASH = "Cash"
        const val CARD = "Card"
        const val UPI = "UPI"
        const val WALLET = "Wallet"
        
        val ALL = listOf(CASH, CARD, UPI, WALLET)
    }

    object ValidationMessages {
        const val PHONE_REQUIRED = "Phone number is required"
        const val PASSWORD_REQUIRED = "Password is required"
        const val PHONE_INVALID = "Invalid phone number"
        const val PASSWORD_SHORT = "Password must be at least 8 characters"
        const val CART_EMPTY = "Cart is empty"
        const val ITEM_REQUIRED = "Please select at least one item"
        const val INVALID_AMOUNT = "Invalid amount"
    }

    object ErrorMessages {
        const val NETWORK_ERROR = "Network error. Please check your connection."
        const val SERVER_ERROR = "Server error. Please try again later."
        const val AUTH_FAILED = "Authentication failed"
        const val SESSION_EXPIRED = "Session expired. Please login again."
        const val UNKNOWN_ERROR = "Something went wrong"
        const val PERMISSION_DENIED = "Permission denied"
        const val NOT_FOUND = "Resource not found"
    }

    object SuccessMessages {
        const val LOGIN_SUCCESS = "Login successful"
        const val ORDER_CREATED = "Order created successfully"
        const val MENU_IMPORTED = "Menu items imported successfully"
        const val SETTINGS_SAVED = "Settings saved"
        const val LOGOUT_SUCCESS = "Logged out successfully"
        const val PAYMENT_SUCCESS = "Payment successful"
    }

    object ApiEndpoints {
        const val LOGIN = "/api/auth/login"
        const val SIGNUP = "/api/auth/register"
        const val REFRESH_TOKEN = "/api/auth/refresh"
        const val LOGOUT = "/api/auth/logout"
        const val MASTER_SYNC = "/api/sync/master"
        const val BILLS = "/api/bills"
        const val ORDERS = "/api/orders"
        const val MENU_SYNC = "/api/menu/sync"
        const val PROFILE = "/api/profile"
        const val REPORTS = "/api/reports"
    }

    object TimeoutValues {
        const val SHORT = 2000L
        const val MEDIUM = 5000L
        const val LONG = 10000L
        const val EXTRA_LONG = 15000L
        const val VERY_LONG = 30000L
    }

    object Dates {
        const val TODAY = "Today"
        const val THIS_WEEK = "This Week"
        const val THIS_MONTH = "This Month"
        const val LAST_7_DAYS = "Last 7 Days"
        const val LAST_30_DAYS = "Last 30 Days"
        const val CUSTOM_RANGE = "Custom Range"
    }

    object Reports {
        const val TODAY_REVENUE = 2500.00
        const val WEEK_REVENUE = 15000.00
        const val MONTH_REVENUE = 65000.00
        const val TODAY_ORDERS = 25
        const val WEEK_ORDERS = 150
        const val MONTH_ORDERS = 650
    }

    object DeepLinks {
        const val BILL_DEEP_LINK = "khanabook://bill/ORD-001"
        const val MENU_DEEP_LINK = "khanabook://menu"
        const val SETTINGS_DEEP_LINK = "khanabook://settings"
        const val INVALID_DEEP_LINK = "khanabook://invalid"
    }

    object DeviceDimensions {
        const val SMALL_WIDTH = 360
        const val SMALL_HEIGHT = 640
        const val MEDIUM_WIDTH = 393
        const val MEDIUM_HEIGHT = 852
        const val LARGE_WIDTH = 412
        const val LARGE_HEIGHT = 915
        const val TABLET_WIDTH = 800
        const val TABLET_HEIGHT = 1280
    }

    object FontScales {
        const val SMALL = 0.85f
        const val NORMAL = 1.0f
        const val LARGE = 1.3f
        const val EXTRA_LARGE = 1.5f
        const val ACCESSIBILITY = 2.0f
    }

    object TouchTargets {
        const val MIN_SIZE_DP = 48
        const val MIN_SIZE_WITH_PADDING_DP = 56
    }

    object OrderIds {
        const val ORDER_001 = "ORD-001"
        const val ORDER_002 = "ORD-002"
        const val ORDER_003 = "ORD-003"
    }

    object BillIds {
        const val BILL_001 = "BILL-001"
        const val BILL_002 = "BILL-002"
        const val BILL_003 = "BILL-003"
    }
}
