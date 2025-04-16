# ===========================================
# 檔案：models/book.py (Module 中的 Class)
# ===========================================

class Book:
    """書籍類別 - 展示 Class 的概念"""

    def __init__(self, title, author, price, isbn):
        self.title = title
        self.author = author
        self.price = price
        self.isbn = isbn
        self.is_available = True

    def __str__(self):
        return f"《{self.title}》 by {self.author} - ${self.price}"

    def sell(self):
        """銷售書籍"""
        if self.is_available:
            self.is_available = False
            return True
        return False

    def restock(self):
        """補貨"""
        self.is_available = True