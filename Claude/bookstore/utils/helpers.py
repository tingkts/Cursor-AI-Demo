# ===========================================
# 檔案：utils/helpers.py (工具模組)
# ===========================================

def format_price(price):
    """格式化價格顯示"""
    return f"${price:.2f}"

def validate_isbn(isbn):
    """簡單的 ISBN 驗證"""
    # 移除連字符
    isbn = isbn.replace('-', '').replace(' ', '')

    # 檢查長度（ISBN-10 或 ISBN-13）
    if len(isbn) not in [10, 13]:
        return False

    # 檢查是否都是數字（最後一位可能是 X）
    if not isbn[:-1].isdigit():
        return False

    return True

def generate_book_report(books):
    """生成書籍報告"""
    total_books = len(books)
    available_books = sum(1 for book in books if book.is_available)
    sold_books = total_books - available_books

    report = f"""
    === 書店庫存報告 ===
    總書籍數量: {total_books}
    可售書籍: {available_books}
    已售書籍: {sold_books}

    詳細清單:
    """

    for book in books:
        status = "可售" if book.is_available else "已售"
        report += f"- {book} [{status}]\n"

    return report