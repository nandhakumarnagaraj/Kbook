// Sample data for high-fidelity prototypes only.
// NOT for production. Angular implementation must wire real API services.

export type OrderStatus = "paid" | "pending" | "refunded" | "partial_refund" | "cancelled";

export interface Order {
  id: string;
  time: string;
  items: string;
  itemCount: number;
  mode: "UPI" | "Card" | "Cash";
  status: OrderStatus;
  amount: number;
  refunded?: number;
}

export const SAMPLE_ORDERS: Order[] = [
  {
    id: "KB-2418",
    time: "13:42",
    items: "Paneer Butter Masala, Butter Naan ×2, Jeera Rice",
    itemCount: 4,
    mode: "UPI",
    status: "paid",
    amount: 682,
  },
  {
    id: "KB-2417",
    time: "13:31",
    items: "Masala Dosa ×2, Filter Coffee ×2",
    itemCount: 4,
    mode: "Card",
    status: "paid",
    amount: 380,
  },
  {
    id: "KB-2416",
    time: "13:24",
    items: "Chicken Biryani, Raita, Gulab Jamun",
    itemCount: 3,
    mode: "UPI",
    status: "partial_refund",
    amount: 540,
    refunded: 120,
  },
  {
    id: "KB-2415",
    time: "13:18",
    items: "Veg Thali, Buttermilk",
    itemCount: 2,
    mode: "Cash",
    status: "paid",
    amount: 280,
  },
  {
    id: "KB-2414",
    time: "13:02",
    items: "Hakka Noodles, Manchurian, Coke",
    itemCount: 3,
    mode: "UPI",
    status: "refunded",
    amount: 460,
    refunded: 460,
  },
  {
    id: "KB-2413",
    time: "12:54",
    items: "Idli Sambar, Vada, Coffee",
    itemCount: 3,
    mode: "Cash",
    status: "paid",
    amount: 190,
  },
  {
    id: "KB-2412",
    time: "12:41",
    items: "Tandoori Roti ×4, Dal Makhani, Salad",
    itemCount: 6,
    mode: "UPI",
    status: "pending",
    amount: 520,
  },
];

export const KPIS = [
  {
    label: "Today's Revenue",
    value: "₹48,320",
    delta: +12.4,
    spark: [12, 18, 15, 22, 19, 28, 34],
    hero: true,
  },
  { label: "Orders", value: "184", delta: +8.1, spark: [8, 10, 12, 11, 14, 16, 18] },
  {
    label: "Avg Order Value",
    value: "₹262",
    delta: -3.2,
    spark: [280, 275, 270, 265, 268, 262, 260],
  },
  { label: "Refunds", value: "₹1,240", delta: +2, spark: [0, 2, 1, 3, 2, 4, 3], danger: true },
];

export const REVENUE_TREND = [
  { day: "Mon", value: 32100 },
  { day: "Tue", value: 38400 },
  { day: "Wed", value: 29800 },
  { day: "Thu", value: 41200 },
  { day: "Fri", value: 52600 },
  { day: "Sat", value: 61800 },
  { day: "Sun", value: 48320 },
];

export const RESTAURANT = {
  name: "Spice Garden",
  branch: "Koramangala",
  owner: "Ravi Menon",
};

/* Menu */
export type FoodType = "veg" | "non_veg" | "egg";
export interface MenuItem {
  id: string;
  name: string;
  category: string;
  type: FoodType;
  price: number;
  available: boolean;
  description?: string;
}
export const MENU_CATEGORIES = [
  "Starters",
  "Mains",
  "Rice & Biryani",
  "Breads",
  "Beverages",
  "Desserts",
];
export const SAMPLE_MENU: MenuItem[] = [
  {
    id: "m-001",
    name: "Paneer Butter Masala",
    category: "Mains",
    type: "veg",
    price: 320,
    available: true,
    description: "Creamy tomato gravy, house paneer.",
  },
  {
    id: "m-002",
    name: "Chicken Biryani",
    category: "Rice & Biryani",
    type: "non_veg",
    price: 380,
    available: true,
    description: "Long-grain basmati, dum-cooked.",
  },
  { id: "m-003", name: "Masala Dosa", category: "Mains", type: "veg", price: 180, available: true },
  { id: "m-004", name: "Butter Naan", category: "Breads", type: "veg", price: 60, available: true },
  { id: "m-005", name: "Egg Curry", category: "Mains", type: "egg", price: 240, available: false },
  {
    id: "m-006",
    name: "Filter Coffee",
    category: "Beverages",
    type: "veg",
    price: 60,
    available: true,
  },
  {
    id: "m-007",
    name: "Gulab Jamun (2 pc)",
    category: "Desserts",
    type: "veg",
    price: 90,
    available: true,
  },
  {
    id: "m-008",
    name: "Tandoori Chicken (Half)",
    category: "Starters",
    type: "non_veg",
    price: 340,
    available: true,
  },
  {
    id: "m-009",
    name: "Veg Hakka Noodles",
    category: "Mains",
    type: "veg",
    price: 220,
    available: false,
  },
  {
    id: "m-010",
    name: "Fresh Lime Soda",
    category: "Beverages",
    type: "veg",
    price: 80,
    available: true,
  },
];

/* Staff */
export type StaffRole = "MANAGER" | "CASHIER" | "SERVER" | "KITCHEN";
export interface Staff {
  id: string;
  name: string;
  phone: string;
  email: string;
  role: StaffRole;
  active: boolean;
  joined: string;
}
export const SAMPLE_STAFF: Staff[] = [
  {
    id: "s-001",
    name: "Anita Rao",
    phone: "+91 98450 12345",
    email: "anita@spicegarden.in",
    role: "MANAGER",
    active: true,
    joined: "Mar 2024",
  },
  {
    id: "s-002",
    name: "Rahul Kumar",
    phone: "+91 98450 67890",
    email: "rahul@spicegarden.in",
    role: "CASHIER",
    active: true,
    joined: "Jun 2024",
  },
  {
    id: "s-003",
    name: "Priya Nair",
    phone: "+91 98450 11223",
    email: "priya@spicegarden.in",
    role: "SERVER",
    active: true,
    joined: "Aug 2024",
  },
  {
    id: "s-004",
    name: "Mohammed Iqbal",
    phone: "+91 98450 44556",
    email: "iqbal@spicegarden.in",
    role: "KITCHEN",
    active: false,
    joined: "Jan 2024",
  },
];

/* Terminals */
export type TerminalStatus = "active" | "inactive" | "pending" | "recovery" | "deactivated";
export interface Terminal {
  id: string;
  name: string;
  deviceId: string;
  status: TerminalStatus;
  lastSeen: string;
  model: string;
}
export const SAMPLE_TERMINALS: Terminal[] = [
  {
    id: "t-001",
    name: "Counter POS",
    deviceId: "KB-DEV-88F3A21",
    status: "active",
    lastSeen: "2 min ago",
    model: "Sunmi V2s Plus",
  },
  {
    id: "t-002",
    name: "Kitchen Display",
    deviceId: "KB-DEV-88F3A22",
    status: "active",
    lastSeen: "just now",
    model: 'iPad 10.2"',
  },
  {
    id: "t-003",
    name: "Takeaway Kiosk",
    deviceId: "KB-DEV-88F3A23",
    status: "inactive",
    lastSeen: "4h ago",
    model: "Sunmi K2",
  },
  {
    id: "t-004",
    name: "Manager Tablet",
    deviceId: "KB-DEV-88F3A24",
    status: "recovery",
    lastSeen: "3d ago",
    model: "Samsung Tab A",
  },
];

export const SAMPLE_ACTIVATION_REQUESTS = [
  {
    id: "req-001",
    deviceId: "KB-DEV-9922C11",
    requestedName: "Terrace POS",
    requestedAt: "12 min ago",
    model: "Sunmi V2s Plus",
  },
  {
    id: "req-002",
    deviceId: "KB-DEV-9922C12",
    requestedName: "Bar Counter",
    requestedAt: "1h ago",
    model: "Sunmi K2 Mini",
  },
];

/* Marketplace */
export interface MarketplaceProvider {
  key: "zomato" | "swiggy";
  name: string;
  status: "connected" | "incomplete" | "disabled" | "error";
  restaurantId?: string;
  webhookUrl: string;
}
export const SAMPLE_MARKETPLACES: MarketplaceProvider[] = [
  {
    key: "zomato",
    name: "Zomato",
    status: "connected",
    restaurantId: "ZOM-882194",
    webhookUrl: "https://kbook.iadv.cloud/api/v1/webhooks/zomato/882194",
  },
  {
    key: "swiggy",
    name: "Swiggy",
    status: "incomplete",
    restaurantId: "",
    webhookUrl: "https://kbook.iadv.cloud/api/v1/webhooks/swiggy/pending",
  },
];

/* Platform admin */
export interface Business {
  id: string;
  name: string;
  owner: string;
  city: string;
  status: "active" | "suspended" | "pending";
  plan: "Starter" | "Growth" | "Scale";
  revenue30d: number;
  terminals: number;
  created: string;
}
export const SAMPLE_BUSINESSES: Business[] = [
  {
    id: "b-001",
    name: "Spice Garden",
    owner: "Ravi Menon",
    city: "Bengaluru",
    status: "active",
    plan: "Growth",
    revenue30d: 1284500,
    terminals: 2,
    created: "Jan 2024",
  },
  {
    id: "b-002",
    name: "Anand's Tiffin Room",
    owner: "Anand Iyer",
    city: "Chennai",
    status: "active",
    plan: "Starter",
    revenue30d: 342800,
    terminals: 1,
    created: "Mar 2024",
  },
  {
    id: "b-003",
    name: "Karim's Kebabs & Curries",
    owner: "Karim Ahmed",
    city: "Delhi",
    status: "active",
    plan: "Scale",
    revenue30d: 2891200,
    terminals: 5,
    created: "Nov 2023",
  },
  {
    id: "b-004",
    name: "Dosa Corner",
    owner: "Lakshmi V.",
    city: "Hyderabad",
    status: "pending",
    plan: "Starter",
    revenue30d: 0,
    terminals: 0,
    created: "3d ago",
  },
  {
    id: "b-005",
    name: "Kolkata Kathi Rolls",
    owner: "Subir Ghosh",
    city: "Kolkata",
    status: "suspended",
    plan: "Growth",
    revenue30d: 0,
    terminals: 3,
    created: "Aug 2024",
  },
  {
    id: "b-006",
    name: "Malabar Coast",
    owner: "Neha Menon",
    city: "Kochi",
    status: "active",
    plan: "Growth",
    revenue30d: 782300,
    terminals: 2,
    created: "Apr 2024",
  },
];

export const PLATFORM_KPIS = [
  {
    label: "Total businesses",
    value: "184",
    delta: +6.2,
    spark: [140, 148, 155, 162, 170, 178, 184],
  },
  {
    label: "Live businesses",
    value: "162",
    delta: +4.1,
    spark: [130, 138, 145, 150, 155, 160, 162],
  },
  {
    label: "Total staff",
    value: "1,428",
    delta: +3.4,
    spark: [1200, 1250, 1290, 1330, 1370, 1400, 1428],
  },
  {
    label: "Platform revenue (30d)",
    value: "₹1.42 Cr",
    delta: +12.8,
    spark: [96, 102, 110, 118, 126, 134, 142],
  },
];
