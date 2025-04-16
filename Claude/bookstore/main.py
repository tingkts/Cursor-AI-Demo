# ===========================================
# 檔案：main.py (主程式)
# ===========================================

# 導入我們的 package 和 modules
from models import Book, Customer  # 從 models package 導入
from utils import format_price, validate_isbn, generate_book_report  # 從 utils package 導入

def main():
    print("=== Python Class, Module, Package 示例 ===\n")

    # 1. 使用 Class 創建物件
    print("1. 創建書籍物件 (Class 使用)")
    book1 = Book("Python 程式設計", "張三", 580, "978-123-456-789-0")
    book2 = Book("資料結構與演算法", "李四", 720, "978-987-654-321-0")
    book3 = Book("機器學習入門", "王五", 650, "978-555-666-777-8")

    books = [book1, book2, book3]

    for book in books:
        print(f"  {book}")
    print()

    # 2. 使用工具模組的函數 (Module 使用)
    print("2. 使用工具模組函數 (Module 使用)")
    for book in books:
        isbn_valid = "有效" if validate_isbn(book.isbn) else "無效"
        formatted_price = format_price(book.price)
        print(f"  {book.title}: ISBN {isbn_valid}, 價格 {formatted_price}")
    print()

    # 3. 創建顧客並進行交易
    print("3. 顧客購買流程 (Class 互動)")
    customer1 = Customer("小明", "xiaoming@email.com")
    customer2 = Customer("小華", "xiaohua@email.com")

    # 顧客購買書籍
    print(f"  {customer1.buy_book(book1)}")
    print(f"  {customer2.buy_book(book2)}")
    print(f"  {customer1.buy_book(book3)}")
    print()

    # 4. 查看購買記錄
    print("4. 購買記錄")
    print(customer1.get_purchase_history())
    print(customer2.get_purchase_history())

    # 5. 生成報告 (使用 utils package 的函數)
    print("5. 庫存報告 (Package 功能整合)")
    report = generate_book_report(books)
    print(report)

if __name__ == "__main__":
    main()