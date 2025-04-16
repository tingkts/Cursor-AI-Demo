# ===========================================
# 檔案：models/customer.py (另一個 Class)
# ===========================================

class Customer:
    """顧客類別"""

    def __init__(self, name, email):
        self.name = name
        self.email = email
        self.purchased_books = []

    def buy_book(self, book):
        """購買書籍"""
        if book.sell():
            self.purchased_books.append(book)
            return f"{self.name} 成功購買了 {book.title}"
        else:
            return f"抱歉，{book.title} 目前缺貨"

    def get_purchase_history(self):
        """獲取購買記錄"""
        if not self.purchased_books:
            return f"{self.name} 還沒有購買任何書籍"

        history = f"{self.name} 的購買記錄：\n"
        for book in self.purchased_books:
            history += f"- {book}\n"
        return history