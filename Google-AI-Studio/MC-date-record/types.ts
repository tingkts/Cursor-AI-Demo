export interface DateEntry {
  id: string;
  year: number;
  month: number; // 1-12
  day: number;   // 1-31
  dateObject: Date; // UTC Date object
}
