'use client';

import { useState, useRef, useCallback } from 'react';
import { Upload, FileText, X, Check, AlertCircle, Loader2 } from 'lucide-react';

interface FileUploadProps {
    onUploadSuccess: () => void;
    onClose: () => void;
}

export default function FileUpload({ onUploadSuccess, onClose }: FileUploadProps) {
    const [file, setFile] = useState<File | null>(null);
    const [symbol, setSymbol] = useState('');
    const [isDragging, setIsDragging] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleDragOver = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(true);
    }, []);

    const handleDragLeave = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(false);
    }, []);

    const handleDrop = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(false);
        setError(null);

        const droppedFile = e.dataTransfer.files[0];
        if (droppedFile) {
            if (!droppedFile.name.toLowerCase().endsWith('.csv')) {
                setError('Please upload a CSV file');
                return;
            }
            setFile(droppedFile);
            // Auto-fill symbol from filename
            const suggestedSymbol = droppedFile.name.replace('.csv', '').toUpperCase();
            if (!symbol) {
                setSymbol(suggestedSymbol);
            }
        }
    }, [symbol]);

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        setError(null);
        const selectedFile = e.target.files?.[0];
        if (selectedFile) {
            if (!selectedFile.name.toLowerCase().endsWith('.csv')) {
                setError('Please upload a CSV file');
                return;
            }
            setFile(selectedFile);
            // Auto-fill symbol from filename
            const suggestedSymbol = selectedFile.name.replace('.csv', '').toUpperCase();
            if (!symbol) {
                setSymbol(suggestedSymbol);
            }
        }
    };

    const handleUpload = async () => {
        if (!file || !symbol.trim()) {
            setError('Please select a file and enter a symbol');
            return;
        }

        setIsUploading(true);
        setError(null);

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('symbol', symbol.toUpperCase());

            const res = await fetch('/api/v1/data/upload', {
                method: 'POST',
                body: formData,
            });

            const data = await res.json();

            if (data.success) {
                setSuccess(`Successfully uploaded ${data.rows} rows for ${data.symbol}`);
                setTimeout(() => {
                    onUploadSuccess();
                    onClose();
                }, 1500);
            } else {
                setError(data.error || 'Upload failed');
            }
        } catch (err) {
            setError('Failed to upload file. Please try again.');
        } finally {
            setIsUploading(false);
        }
    };

    const handleClearFile = () => {
        setFile(null);
        setError(null);
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    return (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4 animate-fade-in">
            <div className="bg-dark-800 border border-white/10 rounded-2xl w-full max-w-md p-6 shadow-2xl">
                {/* Header */}
                <div className="flex items-center justify-between mb-6">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-accent-primary/20 flex items-center justify-center">
                            <Upload className="w-5 h-5 text-accent-primary" />
                        </div>
                        <h2 className="text-lg font-semibold">Upload Stock Data</h2>
                    </div>
                    <button
                        onClick={onClose}
                        className="text-gray-500 hover:text-white transition-colors"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Drop Zone */}
                <div
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                    onClick={() => fileInputRef.current?.click()}
                    className={`
                        border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-all
                        ${isDragging
                            ? 'border-accent-primary bg-accent-primary/10'
                            : 'border-white/10 hover:border-white/30 hover:bg-white/5'
                        }
                        ${file ? 'border-accent-success bg-accent-success/10' : ''}
                    `}
                >
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept=".csv"
                        onChange={handleFileSelect}
                        className="hidden"
                    />

                    {file ? (
                        <div className="flex items-center justify-center gap-3">
                            <FileText className="w-8 h-8 text-accent-success" />
                            <div className="text-left">
                                <p className="font-medium text-white">{file.name}</p>
                                <p className="text-xs text-gray-500">
                                    {(file.size / 1024).toFixed(1)} KB
                                </p>
                            </div>
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    handleClearFile();
                                }}
                                className="ml-2 text-gray-500 hover:text-accent-danger transition-colors"
                            >
                                <X className="w-4 h-4" />
                            </button>
                        </div>
                    ) : (
                        <>
                            <Upload className="w-10 h-10 text-gray-500 mx-auto mb-3" />
                            <p className="text-white font-medium mb-1">
                                Drag & drop CSV file here
                            </p>
                            <p className="text-sm text-gray-500">
                                or click to browse
                            </p>
                        </>
                    )}
                </div>

                {/* Symbol Input */}
                <div className="mt-4">
                    <label className="block text-sm text-gray-400 mb-2">
                        Stock Symbol
                    </label>
                    <input
                        type="text"
                        value={symbol}
                        onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                        placeholder="e.g., TSLA, GOOGL"
                        className="input"
                        maxLength={10}
                    />
                </div>

                {/* Format Hint */}
                <div className="mt-4 p-3 bg-dark-700 rounded-lg text-xs text-gray-500">
                    <p className="font-medium text-gray-400 mb-1">Required CSV format:</p>
                    <code className="text-accent-primary">
                        date, open, high, low, close, volume
                    </code>
                </div>

                {/* Error/Success Messages */}
                {error && (
                    <div className="mt-4 p-3 bg-accent-danger/10 border border-accent-danger/30 rounded-lg flex items-center gap-2 text-accent-danger text-sm">
                        <AlertCircle className="w-4 h-4 flex-shrink-0" />
                        {error}
                    </div>
                )}

                {success && (
                    <div className="mt-4 p-3 bg-accent-success/10 border border-accent-success/30 rounded-lg flex items-center gap-2 text-accent-success text-sm">
                        <Check className="w-4 h-4 flex-shrink-0" />
                        {success}
                    </div>
                )}

                {/* Actions */}
                <div className="flex gap-3 mt-6">
                    <button
                        onClick={onClose}
                        className="flex-1 px-4 py-3 rounded-xl border border-white/10 text-gray-400 hover:bg-white/5 transition-colors"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleUpload}
                        disabled={!file || !symbol.trim() || isUploading}
                        className="flex-1 btn-primary flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {isUploading ? (
                            <>
                                <Loader2 className="w-4 h-4 animate-spin" />
                                Uploading...
                            </>
                        ) : (
                            <>
                                <Upload className="w-4 h-4" />
                                Upload
                            </>
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}
