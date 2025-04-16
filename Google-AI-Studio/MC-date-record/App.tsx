
import React, { useState, useCallback, useEffect } from 'react';
import { DateEntry } from './types';
import DateList from './components/DateList';
import { RestoreIcon } from './components/icons';

const App: React.FC = () => {
  const [selectedDateInput, setSelectedDateInput] = useState<string>(''); // Stores "YYYY-MM-DD"
  
  const [dateEntries, setDateEntries] = useState<DateEntry[]>([]);
  const [backup, setBackup] = useState<DateEntry[] | null>(null);
  
  const [message, setMessage] = useState<string | null>(null);
  const [messageType, setMessageType] = useState<'error' | 'success' | null>(null);

  const displayMessage = (text: string, type: 'error' | 'success') => {
    setMessage(text);
    setMessageType(type);
    setTimeout(() => {
      setMessage(null);
      setMessageType(null);
    }, 3000);
  };

  const handleAddDate = useCallback(() => {
    if (!selectedDateInput) {
      displayMessage("請選擇一個日期。", 'error');
      return;
    }

    const parts = selectedDateInput.split('-');
    if (parts.length !== 3) {
        displayMessage("日期格式不正確。", 'error'); // Should ideally not happen with type="date"
        return;
    }

    const year = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10); // This is 1-12
    const day = parseInt(parts[2], 10);   // This is 1-31

    if (isNaN(year) || isNaN(month) || isNaN(day)) {
        displayMessage("選取的日期包含無效數字。", 'error');
        return;
    }

    // Create Date object in UTC. Month for Date constructor is 0-indexed.
    const newDateObject = new Date(Date.UTC(year, month - 1, day));

    // Validate if the created Date object correctly represents the input
    // This catches invalid dates like February 30th if somehow passed, 
    // though <input type="date"> should prevent selecting these.
    if (newDateObject.getUTCFullYear() !== year || 
        newDateObject.getUTCMonth() !== month - 1 || 
        newDateObject.getUTCDate() !== day) {
        displayMessage("選擇的日期無效 (例如，日期不存在)。", 'error');
        return;
    }
    
    const newEntry: DateEntry = {
      id: crypto.randomUUID(),
      year, // Store actual year, month, day as entered/selected
      month,
      day,
      dateObject: newDateObject,
    };

    setDateEntries(prevEntries => {
        const updatedEntries = [...prevEntries, newEntry];
        // Ensure entries are sorted by date after adding
        updatedEntries.sort((a,b) => a.dateObject.getTime() - b.dateObject.getTime());
        return updatedEntries;
    });
    
    setSelectedDateInput(''); // Clear the date input
    displayMessage("日期已成功新增！", 'success');
  }, [selectedDateInput]);

  const handleDeleteDate = useCallback((id: string) => {
    setBackup([...dateEntries]); 
    setDateEntries(prevEntries => prevEntries.filter(entry => entry.id !== id));
    displayMessage("日期已刪除。您可以還原上次刪除的資料。", 'success');
  }, [dateEntries]);

  const handleRestoreBackup = useCallback(() => {
    if (backup) {
      setDateEntries(backup);
      setBackup(null); 
      displayMessage("資料已成功還原！", 'success');
    } else {
      displayMessage("沒有可還原的備份資料。", 'error');
    }
  }, [backup]);

  useEffect(() => {
    // Clear error message if user starts typing/selecting a new date
    if (selectedDateInput) {
        if (message && messageType === 'error') { 
            setMessage(null);
            setMessageType(null);
        }
    }
  }, [selectedDateInput, message, messageType]);

  return (
    <div className="container mx-auto p-4 sm:p-6 md:p-8 max-w-3xl font-sans bg-slate-50 min-h-screen">
      <header className="mb-8 text-center">
        <h1 className="text-3xl sm:text-4xl font-bold text-slate-800 tracking-tight">月~記錄</h1>
        <p className="text-slate-600 mt-2 text-lg">輕鬆記錄與追蹤重要日期</p>
      </header>

      {message && (
        <div className={`mb-6 p-4 rounded-md text-sm shadow-md ${messageType === 'error' ? 'bg-red-100 text-red-800 border border-red-200' : 'bg-green-100 text-green-800 border border-green-200'}`} role="alert">
          <p className="font-medium">{messageType === 'error' ? '錯誤' : '成功'}: <span className="font-normal">{message}</span></p>
        </div>
      )}

      <section className="mb-10 p-6 bg-white shadow-xl rounded-lg border border-slate-200">
        <h2 className="text-2xl font-semibold mb-6 text-slate-700">新增日期</h2>
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-x-4 gap-y-5 items-end">
          <div className="sm:col-span-3">
            <label htmlFor="date-picker" className="block text-sm font-medium text-slate-600 mb-1.5">選擇日期</label>
            <input
              type="date"
              id="date-picker"
              value={selectedDateInput}
              onChange={(e) => setSelectedDateInput(e.target.value)}
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-md shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all duration-150 ease-in-out text-slate-700 placeholder-slate-400"
            />
          </div>
          
          <button
            onClick={handleAddDate}
            className="sm:col-span-1 w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2.5 px-4 rounded-md shadow-md hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-opacity-75 transition-all duration-150 ease-in-out"
          >
            加入日期
          </button>
        </div>
      </section>

      <section className="mb-8 p-6 bg-white shadow-xl rounded-lg border border-slate-200">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-6 gap-4">
          <h2 className="text-2xl font-semibold text-slate-700">月~</h2>
          {backup && (
             <button
                onClick={handleRestoreBackup}
                disabled={!backup}
                className="flex items-center bg-yellow-500 hover:bg-yellow-600 text-white font-semibold py-2 px-4 rounded-md shadow-md hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-yellow-500 focus:ring-opacity-75 transition-all duration-150 ease-in-out disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:bg-yellow-500"
              >
              <RestoreIcon className="w-5 h-5 mr-2" />
              回復上次刪除
            </button>
          )}
        </div>
        <DateList dateEntries={dateEntries} onDeleteDate={handleDeleteDate} />
      </section>
      
      <footer className="text-center text-sm text-slate-500 mt-12 py-6 border-t border-slate-200">
        <p>&copy; {new Date().getFullYear()} 月~記錄應用程式. All rights reserved.</p>
        <p className="mt-1">Designed with Tailwind CSS & React.</p>
      </footer>
    </div>
  );
};

export default App;